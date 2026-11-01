package com.freetune.app.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Track
import com.freetune.app.data.settings.SettingsStore
import com.freetune.app.data.yt.AndroidDownloader
import com.freetune.app.data.yt.YouTubeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val autoplayLoading: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class PlayerManager(
    private val context: Context,
    private val settings: SettingsStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val httpDataSource = DefaultHttpDataSource.Factory()
        .setUserAgent(AndroidDownloader.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, httpDataSource)
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var index = -1
    private var loadJob: Job? = null
    private var autoplayJob: Job? = null
    private var retryCount = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                }
                _state.value = _state.value.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    durationMs = if (player.duration > 0L) player.duration
                    else _state.value.current?.durationMs ?: 0L
                )
                if (playbackState == Player.STATE_ENDED) {
                    onTrackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = _state.value.current
                if (current != null && current.localPath == null && retryCount < 1) {
                    retryCount++
                    scope.launch { playCurrent() }
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Playback failed"
                    )
                }
            }
        })

        scope.launch {
            while (isActive) {
                val current = _state.value.current
                if (current != null) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = if (player.duration > 0L) player.duration
                        else current.durationMs
                    )
                }
                delay(500)
            }
        }
    }

    fun play(track: Track, newQueue: List<Track> = listOf(track)) {
        retryCount = 0
        autoplayJob?.cancel()
        autoplayJob = null
        _state.value = _state.value.copy(autoplayLoading = false)
        val proposed = newQueue.ifEmpty { listOf(track) }
        queue = if (proposed.any { it.id == track.id }) proposed else listOf(track)
        index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playCurrent()
    }

    private fun playCurrent() {
        val track = queue.getOrNull(index) ?: return
        loadJob?.cancel()
        _state.value = _state.value.copy(
            current = track,
            queue = queue,
            isLoading = true,
            error = null,
            positionMs = 0L,
            durationMs = track.durationMs
        )
        startService()

        loadJob = scope.launch {
            try {
                val localPath = track.localPath?.takeIf { File(it).exists() }
                val uri = if (localPath != null) {
                    Uri.fromFile(File(localPath))
                } else {
                    val source = withContext(Dispatchers.IO) {
                        YouTubeService.getPlaybackSource(track.id, settings.quality.value)
                    }
                    Uri.parse(source.url)
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                    .build()

                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadata)
                        .build()
                )
                player.prepare()
                player.playWhenReady = true
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = t.message ?: "Could not load audio"
                )
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (index < queue.lastIndex) {
            autoplayJob?.cancel()
            autoplayJob = null
            retryCount = 0
            index++
            playCurrent()
        } else {
            onTrackEnded()
        }
    }

    fun previous() {
        autoplayJob?.cancel()
        autoplayJob = null
        _state.value = _state.value.copy(autoplayLoading = false)
        if (index > 0) {
            retryCount = 0
            index--
            playCurrent()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun stop() {
        loadJob?.cancel()
        autoplayJob?.cancel()
        autoplayJob = null
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        index = -1
        _state.value = PlayerUiState()
    }

    private fun onTrackEnded() {
        if (index < queue.lastIndex) {
            index++
            playCurrent()
            return
        }

        val seed = queue.getOrNull(index) ?: return
        if (!settings.autoplay.value) {
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        if (autoplayJob?.isActive == true) return

        val knownIds = queue.map { it.id }.toSet()
        _state.value = _state.value.copy(autoplayLoading = true, error = null)

        autoplayJob = scope.launch {
            try {
                val related = YouTubeService.related(
                    videoId = seed.id,
                    seedTitle = seed.title,
                    seedArtist = seed.artist,
                    limit = 20
                ).filter { it.id !in knownIds }

                _state.value = _state.value.copy(autoplayLoading = false)

                if (!settings.autoplay.value) return@launch
                if (related.isEmpty()) {
                    _state.value = _state.value.copy(
                        error = "Autoplay couldn't find more songs right now."
                    )
                    return@launch
                }
                if (_state.value.current?.id != seed.id) return@launch

                queue = queue + related.take(15)
                index++
                _state.value = _state.value.copy(queue = queue, error = null)
                playCurrent()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    autoplayLoading = false,
                    error = t.message ?: "Autoplay failed"
                )
            }
        }
    }

    private fun startService() {
        runCatching {
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }
}
