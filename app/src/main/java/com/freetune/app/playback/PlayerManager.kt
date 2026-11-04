package com.freetune.app.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freetune.app.data.model.Track
import com.freetune.app.data.session.SessionStore
import com.freetune.app.data.settings.SettingsStore
import com.freetune.app.data.yt.AndroidDownloader
import com.freetune.app.data.yt.PlaybackSource
import com.freetune.app.data.yt.YouTubeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val autoplayLoading: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Milliseconds left on the sleep timer, 0 when no timer is running. */
    val sleepRemainingMs: Long = 0L,
    /** True when the timer is set to stop after the current song. */
    val sleepAtEnd: Boolean = false
)

@OptIn(UnstableApi::class)
class PlayerManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val session: SessionStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    /** Called whenever a new track starts, including autoplay picks. */
    var onTrackStarted: ((Track) -> Unit)? = null

    private var queue: List<Track> = emptyList()
    private var index = -1
    private var loadJob: Job? = null
    private var autoplayJob: Job? = null
    private var prefetchJob: Job? = null
    private var preparedTrackId: String? = null
    private var sourceRetries = 0
    private var endRecoveries = 0
    private var recoveryMode = false
    private var playingBitrate = 0
    private var markedTrackId: String? = null
    private var lastSessionSave = 0L
    private var lastSkip: SkippedTrack? = null
    private var repeatMode = RepeatMode.OFF
    private var sleepJob: Job? = null
    private var sleepEndsAt = 0L
    private var sleepAtEnd = false

    private data class CachedSource(val source: PlaybackSource, val resolvedAt: Long)

    /** The song and position the user just skipped away from, for the Undo action. */
    private data class SkippedTrack(val index: Int, val positionMs: Long)

    /** Resolved stream URLs, so the next song starts without a fresh extractor call. */
    private val sourceCache = LinkedHashMap<String, CachedSource>()

    /** Related-song lists, so autoplay can extend the queue instantly. */
    private val relatedCache = LinkedHashMap<String, List<Track>>()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (!isPlaying) saveSession()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // Record history only once the song actually starts, not when the
                    // load is still resolving (or fails).
                    val track = _state.value.current
                    if (track != null && markedTrackId != track.id) {
                        markedTrackId = track.id
                        onTrackStarted?.invoke(track)
                    }
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
                if (error.errorCode ==
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                ) {
                    // The source ran out of bytes before its duration was reached:
                    // treat it as the end of the song, not a failure.
                    onTrackEnded()
                    return
                }
                android.util.Log.w(
                    "FreeTunePlayback",
                    "Playback error ${error.errorCodeName} (${error.errorCode}) for ${current?.id}",
                    error
                )
                val position = if (player.mediaItemCount > 0) {
                    player.currentPosition.coerceAtLeast(0L)
                } else {
                    _state.value.positionMs
                }
                if (current != null &&
                    !current.isAvailableOffline &&
                    sourceRetries < MAX_SOURCE_RETRIES
                ) {
                    sourceRetries++
                    // An expired or refused stream URL is fixed by resolving a new
                    // one; a 403 usually clears as well. Space the attempts out so a
                    // rate-limited CDN gets a moment.
                    sourceCache.remove(sourceKey(current))
                    scope.launch {
                        delay(1_000L * sourceRetries)
                        playCurrent(startAtMs = position)
                    }
                    return
                }
                if (current != null && isUnavailable(error) && index < queue.lastIndex) {
                    // The video itself is gone (not a stream URL problem): move on.
                    index++
                    scope.launch { playCurrent() }
                    return
                }
                // Keep the song selected and show why, instead of skipping silently.
                _state.value = _state.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = friendlyError(error)
                )
            }
        })

        scope.launch {
            while (isActive) {
                val current = _state.value.current
                if (current != null && player.mediaItemCount > 0) {
                    val sleepRemaining = if (sleepEndsAt > 0L) {
                        (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = if (player.duration > 0L) player.duration
                        else current.durationMs,
                        sleepRemainingMs = sleepRemaining
                    )
                    // A crash or swipe-away shouldn't lose more than a few seconds.
                    if (player.isPlaying &&
                        System.currentTimeMillis() - lastSessionSave > SESSION_SAVE_MS
                    ) {
                        lastSessionSave = System.currentTimeMillis()
                        saveSession()
                    }
                }
                delay(500)
            }
        }

        restoreSession()
    }

    /**
     * Brings back the queue from the last run, paused at the saved position.
     * Nothing is prepared here, so restoring costs no network: the first tap on
     * play loads the song and seeks to where the user left off.
     */
    private fun restoreSession() {
        val saved = session.load() ?: return
        val track = saved.queue.getOrNull(saved.index) ?: return
        queue = saved.queue
        index = saved.index
        val duration = track.durationMs.coerceAtLeast(0L)
        val savedPosition = saved.positionMs.coerceIn(0L, duration)
        // A position saved at the very end of a song must not resume at the end:
        // it would immediately fire ENDED and skip the song the user wanted back.
        val resumePosition = if (duration > 0L && savedPosition >= duration - 3_000L) {
            0L
        } else {
            savedPosition
        }
        _state.value = PlayerUiState(
            current = track,
            queue = queue,
            isPlaying = false,
            positionMs = resumePosition,
            durationMs = track.durationMs
        )
    }

    private fun saveSession() {
        if (queue.isEmpty()) return
        val position = if (player.mediaItemCount > 0) player.currentPosition
        else _state.value.positionMs
        session.save(queue, index, position)
    }

    fun play(track: Track, newQueue: List<Track> = listOf(track)) {
        preparedTrackId = null
        lastSkip = null
        autoplayJob?.cancel()
        autoplayJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        _state.value = _state.value.copy(autoplayLoading = false, error = null)
        val proposed = newQueue.ifEmpty { listOf(track) }
        queue = if (proposed.any { it.id == track.id }) proposed else listOf(track)
        index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playCurrent()
    }

    /**
     * Plays [track] and immediately fills the queue with related songs, so the
     * station keeps going before the seed track even ends.
     */
    fun startRadio(track: Track) {
        play(track, listOf(track))
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            _state.value = _state.value.copy(autoplayLoading = true, error = null)
            try {
                val related = relatedFor(track)
                    .filter { it.id != track.id }
                    .distinctBy { it.id }
                    .take(AUTOPLAY_BATCH)
                if (_state.value.current?.id != track.id || related.isEmpty()) {
                    _state.value = _state.value.copy(autoplayLoading = false)
                    return@launch
                }
                queue = queue + related
                _state.value = _state.value.copy(queue = queue, autoplayLoading = false)
                session.save(queue, index, player.currentPosition)
                queuePrefetch()
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = _state.value.copy(autoplayLoading = false)
            }
        }
    }

    private fun playCurrent(startAtMs: Long = 0L) {
        val track = queue.getOrNull(index) ?: return
        if (preparedTrackId != track.id) {
            preparedTrackId = track.id
            sourceRetries = 0
            endRecoveries = 0
            recoveryMode = false
            playingBitrate = 0
        }
        loadJob?.cancel()
        prefetchJob?.cancel()
        prefetchJob = null
        _state.value = _state.value.copy(
            current = track,
            queue = queue,
            isLoading = true,
            error = null,
            positionMs = 0L,
            durationMs = track.durationMs
        )
        startService()
        markedTrackId = null
        session.save(queue, index, startAtMs)

        loadJob = scope.launch {
            try {
                val uri = playbackUri(track)
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
                if (startAtMs > 0L) player.seekTo(startAtMs)
                player.playWhenReady = true
                queuePrefetch()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = t.message ?: "Could not load audio"
                )
            }
        }
    }

    private suspend fun playbackUri(track: Track): Uri {
        track.contentUri?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        val localPath = track.localPath
        if (!localPath.isNullOrBlank()) {
            if (File(localPath).exists()) return Uri.fromFile(File(localPath))
            if (track.isLocalFile) throw IOException("This file is no longer on your device.")
        }
        val source = sourceFor(track, recovery = recoveryMode)
        playingBitrate = source.bitrate
        return Uri.parse(source.url)
    }

    private suspend fun sourceFor(track: Track, recovery: Boolean = false): PlaybackSource {
        val key = sourceKey(track)
        // A recovery must not reuse the URL that just stopped early.
        val cached = if (recovery) null else sourceCache[key]
        if (cached != null && System.currentTimeMillis() - cached.resolvedAt < SOURCE_TTL_MS) {
            return cached.source
        }
        val source = withContext(Dispatchers.IO) {
            YouTubeService.getPlaybackSource(
                videoId = track.id,
                quality = settings.quality.value,
                excludeBitrate = playingBitrate.takeIf { recovery && it > 0 }
            )
        }
        if (!recovery) {
            sourceCache[key] = CachedSource(source, System.currentTimeMillis())
            trim(sourceCache, MAX_CACHED_SOURCES)
        }
        return source
    }

    /**
     * Resolves the next stream URL (or, at the end of the queue, the related songs)
     * while the current track plays. This is what keeps autoplay from stalling
     * between songs.
     */
    private fun queuePrefetch() {
        val from = index
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            try {
                prefetchAfter(from)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Prefetch is best-effort; playback still works without it.
            }
        }
    }

    private suspend fun prefetchAfter(currentIndex: Int) {
        val next = queue.getOrNull(currentIndex + 1)
        if (next != null) {
            if (!next.isAvailableOffline) sourceFor(next)
            return
        }
        if (!settings.autoplay.value) return
        val seed = queue.getOrNull(currentIndex) ?: return
        val candidate = relatedFor(seed)
            .firstOrNull { track -> queue.none { it.id == track.id } }
            ?: return
        if (!candidate.isAvailableOffline) sourceFor(candidate)
    }

    private suspend fun relatedFor(seed: Track): List<Track> {
        relatedCache[seed.id]?.let { return it }
        // An imported file has no YouTube id, so asking for related streams would
        // just 404. Search for the song instead and continue from the results.
        val related = if (seed.isLocalFile) {
            val query = listOf(
                seed.title,
                seed.artist.takeUnless { it.equals("Unknown artist", ignoreCase = true) }
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) emptyList() else YouTubeService.search(query, 25)
        } else {
            YouTubeService.related(
                videoId = seed.id,
                seedTitle = seed.title,
                seedArtist = seed.artist,
                limit = 25
            )
        }
            .filterNot { it.id == seed.id || it.title.equals(seed.title, ignoreCase = true) }
            .distinctBy { it.id }
        relatedCache[seed.id] = related
        trim(relatedCache, MAX_CACHED_RELATED)
        return related
    }

    fun togglePlayPause() {
        // After the queue ended, playWhenReady is still true while isPlaying is false,
        // so a plain pause() did nothing and the play button looked dead.
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
            return
        }
        // Restored session: nothing is prepared yet, so loading starts on demand.
        if (player.mediaItemCount == 0 && queue.isNotEmpty()) {
            playCurrent(startAtMs = _state.value.positionMs)
            return
        }
        // playWhenReady, not isPlaying: while buffering isPlaying is false and the
        // button used to start playback again instead of pausing it.
        if (player.playWhenReady) player.pause() else player.play()
    }

    /** Skips forward. Returns true when the queue actually moved on. */
    fun next(): Boolean {
        autoplayJob?.cancel()
        autoplayJob = null
        _state.value = _state.value.copy(autoplayLoading = false)
        val position = if (player.mediaItemCount > 0) player.currentPosition else 0L
        if (index < queue.lastIndex) {
            lastSkip = SkippedTrack(index, position.coerceAtLeast(0L))
            index++
            playCurrent()
            return true
        }
        if (repeatMode == RepeatMode.ALL && queue.size > 1) {
            lastSkip = SkippedTrack(index, position.coerceAtLeast(0L))
            index = 0
            playCurrent()
            return true
        }
        if (settings.autoplay.value) {
            // At the end of the queue, "next" extends it. Pretending to pause here
            // left the audio playing while the UI showed a paused player.
            lastSkip = SkippedTrack(index, position.coerceAtLeast(0L))
            onTrackEnded()
            return true
        }
        return false
    }

    fun canUndoSkip(): Boolean = lastSkip != null

    /** Returns to the song that was just skipped, at the position it was left at. */
    fun undoSkip(): Boolean {
        val skipped = lastSkip ?: return false
        if (queue.isEmpty()) return false
        autoplayJob?.cancel()
        autoplayJob = null
        lastSkip = null
        index = skipped.index.coerceIn(0, queue.lastIndex)
        playCurrent(startAtMs = skipped.positionMs)
        return true
    }

    /**
     * Queues [track] directly after the current song. Returns false when it is
     * already the song that is playing.
     */
    fun playNext(track: Track): Boolean = insertIntoQueue(track, index + 1)

    /** Appends [track] to the end of the queue. */
    fun addToQueue(track: Track): Boolean = insertIntoQueue(track, queue.size)

    private fun insertIntoQueue(track: Track, insertAt: Int): Boolean {
        if (queue.isEmpty()) {
            play(track)
            return true
        }
        val currentId = queue.getOrNull(index)?.id
        if (currentId == track.id) return false

        val mutable = queue.toMutableList()
        val existing = mutable.indexOfFirst { it.id == track.id }
        if (existing >= 0) mutable.removeAt(existing)

        // Removing a song before the insertion point shifts it down by one.
        var target = insertAt.coerceIn(0, mutable.size)
        if (existing in 0 until target) target--
        target = target.coerceIn(0, mutable.size)
        mutable.add(target, track)

        // Keep the playing song as the current one, wherever it moved to.
        val newCurrent = mutable.indexOfFirst { it.id == currentId }
        if (newCurrent >= 0) index = newCurrent
        queue = mutable
        lastSkip = null
        // The user is steering the queue now. A pending autoplay batch would
        // otherwise jump in front of the song they just queued.
        autoplayJob?.cancel()
        autoplayJob = null
        _state.value = _state.value.copy(queue = queue, autoplayLoading = false)
        saveSession()
        if (player.mediaItemCount > 0) queuePrefetch()
        return true
    }

    fun previous() {
        autoplayJob?.cancel()
        autoplayJob = null
        _state.value = _state.value.copy(autoplayLoading = false)
        lastSkip = null
        if (index > 0) {
            index--
            playCurrent()
        } else if (repeatMode == RepeatMode.ALL && queue.size > 1) {
            index = queue.lastIndex
            playCurrent()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Off -> all -> one -> off, the order the repeat button cycles through. */
    fun cycleRepeatMode(): RepeatMode {
        val next = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
        return next
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        _state.value = _state.value.copy(repeatMode = mode)
    }

    /** Jumps to a position in the visible queue. */
    fun jumpToQueue(position: Int): Boolean {
        if (position !in queue.indices) return false
        if (position == index) return false
        autoplayJob?.cancel()
        autoplayJob = null
        lastSkip = null
        index = position
        playCurrent()
        return true
    }

    /** Pass null to turn the timer off. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        sleepAtEnd = false
        if (minutes == null || minutes <= 0) {
            sleepEndsAt = 0L
            _state.value = _state.value.copy(sleepRemainingMs = 0L, sleepAtEnd = false)
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        sleepEndsAt = endsAt
        _state.value = _state.value.copy(
            sleepRemainingMs = minutes * 60_000L,
            sleepAtEnd = false
        )
        sleepJob = scope.launch {
            val wait = endsAt - System.currentTimeMillis()
            if (wait > 0L) delay(wait)
            sleepEndsAt = 0L
            player.pause()
            _state.value = _state.value.copy(isPlaying = false, sleepRemainingMs = 0L)
        }
    }

    fun setSleepAtEndOfTrack() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAt = 0L
        sleepAtEnd = true
        _state.value = _state.value.copy(sleepRemainingMs = 0L, sleepAtEnd = true)
    }

    fun cancelSleepTimer() = setSleepTimer(null)

    fun stop() {
        loadJob?.cancel()
        autoplayJob?.cancel()
        prefetchJob?.cancel()
        sleepJob?.cancel()
        autoplayJob = null
        prefetchJob = null
        sleepJob = null
        sleepEndsAt = 0L
        sleepAtEnd = false
        repeatMode = RepeatMode.OFF
        recoveryMode = false
        playingBitrate = 0
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        index = -1
        lastSkip = null
        sourceCache.clear()
        relatedCache.clear()
        session.clear()
        _state.value = PlayerUiState()
    }

    private fun onTrackEnded() {
        // "Stop after this song": pause here instead of moving on.
        if (sleepAtEnd) {
            sleepAtEnd = false
            sleepJob?.cancel()
            sleepJob = null
            player.pause()
            _state.value = _state.value.copy(
                isPlaying = false,
                sleepAtEnd = false,
                sleepRemainingMs = 0L
            )
            return
        }
        val current = _state.value.current
        // A truncated CDN response ends playback well before the advertised
        // duration. Re-resolve once and continue from where it stopped instead of
        // cutting the song off at, say, 80%.
        val advertised = when {
            player.duration > 0L -> player.duration
            current != null -> current.durationMs
            else -> 0L
        }
        val stoppedAt = if (player.mediaItemCount > 0) player.currentPosition else 0L
        if (current != null &&
            !current.isAvailableOffline &&
            advertised > 20_000L &&
            stoppedAt > 1_000L &&
            stoppedAt < advertised - 10_000L
        ) {
            if (endRecoveries < MAX_END_RECOVERIES) {
                endRecoveries++
                // Retry with the uncapped URL and, from the second attempt on, a
                // different audio stream, rather than resolving the same one.
                recoveryMode = true
                sourceCache.remove(sourceKey(current))
                playCurrent(startAtMs = stoppedAt)
                return
            }
            // Every retry still ended early. Do not pretend the song finished and
            // jump to the next one: stop here and let the user decide.
            player.pause()
            _state.value = _state.value.copy(
                isPlaying = false,
                error = "Playback stopped early. Tap play to try again."
            )
            return
        }

        // Repeat modes are handled here rather than through ExoPlayer, because the
        // player only ever holds one media item; the queue lives in this class.
        when (repeatMode) {
            RepeatMode.ONE -> {
                player.seekTo(0L)
                player.play()
                return
            }
            RepeatMode.ALL -> if (index >= queue.lastIndex) {
                index = 0
                playCurrent()
                return
            }
            RepeatMode.OFF -> Unit
        }

        if (index < queue.lastIndex) {
            index++
            playCurrent()
            return
        }

        val seed = queue.getOrNull(index) ?: return
        if (!settings.autoplay.value) {
            // Really pause so playWhenReady, the notification and the UI agree.
            player.pause()
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        if (autoplayJob?.isActive == true) return

        val knownIds = queue.mapTo(mutableSetOf()) { it.id }
        val cached = relatedCache[seed.id]
            ?.filter { it.id !in knownIds }
            ?.distinctBy { it.id }
            ?.take(AUTOPLAY_BATCH)
            .orEmpty()

        // Prefetched while the song was playing: continue without any delay.
        if (cached.isNotEmpty()) {
            appendAutoplay(cached)
            return
        }

        _state.value = _state.value.copy(autoplayLoading = true, error = null)
        autoplayJob = scope.launch {
            try {
                val related = relatedFor(seed)
                    .filter { it.id !in knownIds }
                    .distinctBy { it.id }
                    .take(AUTOPLAY_BATCH)

                if (!settings.autoplay.value) {
                    _state.value = _state.value.copy(autoplayLoading = false)
                    return@launch
                }
                if (_state.value.current?.id != seed.id) {
                    _state.value = _state.value.copy(autoplayLoading = false)
                    return@launch
                }
                if (related.isEmpty()) {
                    _state.value = _state.value.copy(
                        autoplayLoading = false,
                        error = "Autoplay couldn't find more songs right now."
                    )
                    return@launch
                }
                appendAutoplay(related)
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

    private fun appendAutoplay(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val firstNew = queue.size
        queue = queue + tracks
        index = firstNew
        _state.value = _state.value.copy(
            queue = queue,
            autoplayLoading = false,
            error = null
        )
        playCurrent()
    }

    private fun sourceKey(track: Track): String = "${track.id}@${settings.quality.value.name}"

    /**
     * HTTP failures are retried, not treated as a dead video: an expired stream URL
     * answers with 403 and a fresh resolve usually fixes it.
     */
    private fun isUnavailable(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> true
        else -> false
    }

    private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "YouTube refused the audio stream. Tap play to try again."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network error. Check your connection and tap play."
        else -> {
            val message = error.message ?: "Playback failed"
            if (message.contains("source error", ignoreCase = true)) {
                "Source error. Tap play to try again; a fresh stream is resolved each time."
            } else {
                message
            }
        }
    }

    private fun <V> trim(cache: LinkedHashMap<String, V>, max: Int) {
        while (cache.size > max) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    private fun startService() {
        runCatching {
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }

    private companion object {
        const val SOURCE_TTL_MS = 20 * 60 * 1000L
        const val MAX_CACHED_SOURCES = 12
        const val MAX_CACHED_RELATED = 8
        const val AUTOPLAY_BATCH = 15
        const val SESSION_SAVE_MS = 15_000L
        const val MAX_END_RECOVERIES = 3
        const val MAX_SOURCE_RETRIES = 3
    }
}
