package com.freetune.app.ui

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freetune.app.data.AppContainer
import com.freetune.app.data.download.DownloadState
import com.freetune.app.data.download.DownloadStatus
import com.freetune.app.data.download.DownloadStatusStore
import com.freetune.app.data.download.Downloads
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Playlist
import com.freetune.app.data.model.Track
import com.freetune.app.data.yt.YouTubeService
import com.freetune.app.playback.OverlayService
import com.freetune.app.playback.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

data class PlaylistDownloadRequest(
    val playlist: Playlist,
    val tracks: List<Track>
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    val playerState: StateFlow<PlayerUiState> = container.playerManager.state

    val liked: StateFlow<List<Track>> = repository.liked.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val downloads: StateFlow<List<Track>> = repository.downloads.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val recent: StateFlow<List<Track>> = repository.recent.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val playlists: StateFlow<List<Playlist>> = repository.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val likedIds: StateFlow<Set<String>> = liked
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedIds: StateFlow<Set<String>> = downloads
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val quality: StateFlow<AudioQuality> = container.settings.quality
    val wifiOnly: StateFlow<Boolean> = container.settings.wifiOnly
    val floatingControls: StateFlow<Boolean> = container.settings.floatingControls
    val autoplay: StateFlow<Boolean> = container.settings.autoplay
    val downloadStatuses: StateFlow<Map<String, DownloadStatus>> = DownloadStatusStore.items

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _discover = MutableStateFlow(SearchUiState(query = "Top songs 2026"))
    val discover: StateFlow<SearchUiState> = _discover.asStateFlow()

    private val _playlistPicker = MutableStateFlow<Track?>(null)
    val playlistPicker: StateFlow<Track?> = _playlistPicker.asStateFlow()

    private val _playlistDownload = MutableStateFlow<PlaylistDownloadRequest?>(null)
    val playlistDownload: StateFlow<PlaylistDownloadRequest?> = _playlistDownload.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchQueryChange(value: String) {
        _search.update { it.copy(query = value, error = null) }
        searchJob?.cancel()

        val query = value.trim()
        if (query.length < 2) {
            _search.update {
                it.copy(results = emptyList(), loading = false, hasSearched = false)
            }
            return
        }

        _search.update { it.copy(loading = true, hasSearched = true) }
        searchJob = viewModelScope.launch {
            delay(650)
            executeSearch(query, _search)
        }
    }

    fun submitSearch() {
        val query = _search.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(query, _search)
        }
    }

    fun loadDiscover(query: String = _discover.value.query) {
        if (query.isBlank()) return
        _discover.update { it.copy(query = query) }
        viewModelScope.launch { executeSearch(query, _discover) }
    }

    fun ensureDiscoverLoaded() {
        val state = _discover.value
        if (state.results.isEmpty() && !state.loading && state.error == null) {
            loadDiscover(state.query)
        }
    }

    private suspend fun executeSearch(query: String, target: MutableStateFlow<SearchUiState>) {
        target.update { it.copy(loading = true, hasSearched = true, error = null) }
        try {
            val results = YouTubeService.search(query)
            target.update {
                it.copy(results = results, loading = false, hasSearched = true, error = null)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            target.update {
                it.copy(loading = false, hasSearched = true, error = friendly(t))
            }
        }
    }

    fun play(track: Track, queue: List<Track> = listOf(track)) {
        container.playerManager.play(track, queue)
        viewModelScope.launch { repository.markPlayed(track) }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = repository.playlistTracks(playlist.id).first()
            if (tracks.isEmpty()) {
                toast("${playlist.name} is empty")
                return@launch
            }
            startPlaylist(playlist, tracks, shuffle)
        }
    }

    fun playPlaylist(playlist: Playlist, tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) {
            toast("${playlist.name} is empty")
            return
        }
        startPlaylist(playlist, tracks, shuffle)
    }

    private fun startPlaylist(playlist: Playlist, tracks: List<Track>, shuffle: Boolean) {
        val ordered = if (shuffle) tracks.shuffled() else tracks
        container.playerManager.play(ordered.first(), ordered)
        viewModelScope.launch { repository.markPlayed(ordered.first()) }
        toast(if (shuffle) "Shuffling ${playlist.name}" else "Playing ${playlist.name}")
    }

    fun togglePlayPause() = container.playerManager.togglePlayPause()
    fun next() = container.playerManager.next()
    fun previous() = container.playerManager.previous()
    fun seekTo(positionMs: Long) = container.playerManager.seekTo(positionMs)

    fun toggleLike(track: Track) {
        viewModelScope.launch { repository.toggleLike(track) }
    }

    fun download(track: Track, replace: Boolean = false, folder: String? = null) {
        if (!replace) {
            val status = DownloadStatusStore.items.value[track.id]
            if (status != null &&
                (status.state == DownloadState.QUEUED || status.state == DownloadState.RUNNING)
            ) {
                return
            }
            if (downloadedIds.value.contains(track.id)) return
        }
        Downloads.enqueue(
            context = container.context,
            track = track,
            quality = container.settings.quality.value,
            wifiOnly = container.settings.wifiOnly.value,
            replace = replace,
            folder = folder
        )
    }

    fun downloadPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        quality: AudioQuality = container.settings.quality.value,
        skipDownloaded: Boolean = true
    ) {
        if (tracks.isEmpty()) {
            toast("${playlist.name} is empty")
            return
        }
        val activeIds = DownloadStatusStore.items.value
            .filterValues { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
            .keys
        val pending = tracks.filter { track ->
            track.id !in activeIds && (!skipDownloaded || !track.isDownloaded)
        }
        if (pending.isEmpty()) {
            toast("Everything in ${playlist.name} is already downloaded or queued")
            return
        }
        pending.forEach { track ->
            Downloads.enqueue(
                context = container.context,
                track = track,
                quality = quality,
                wifiOnly = container.settings.wifiOnly.value,
                replace = !skipDownloaded,
                folder = playlist.name
            )
        }
        val verb = if (skipDownloaded) "Downloading" else "Re-downloading"
        toast("$verb ${pending.size} songs to ${playlist.name}/")
    }

    fun requestPlaylistDownload(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = repository.playlistTracks(playlist.id).first()
            if (tracks.isEmpty()) {
                toast("${playlist.name} is empty")
                return@launch
            }
            _playlistDownload.value = PlaylistDownloadRequest(playlist, tracks)
        }
    }

    fun dismissPlaylistDownload() {
        _playlistDownload.value = null
    }

    fun confirmPlaylistDownload(quality: AudioQuality, skipDownloaded: Boolean) {
        val request = _playlistDownload.value ?: return
        downloadPlaylist(request.playlist, request.tracks, quality, skipDownloaded)
        _playlistDownload.value = null
    }

    fun retryDownload(status: DownloadStatus) {
        download(status.track, replace = true)
    }

    fun dismissDownload(id: String) = DownloadStatusStore.dismiss(id)

    fun removeDownload(track: Track) {
        viewModelScope.launch { repository.removeDownload(track) }
    }

    fun setQuality(value: AudioQuality) = container.settings.setQuality(value)

    fun setWifiOnly(value: Boolean) = container.settings.setWifiOnly(value)

    fun setFloatingControls(enabled: Boolean) {
        container.settings.setFloatingControls(enabled)
        if (enabled) {
            OverlayService.start(container.context)
        } else {
            OverlayService.stop(container.context)
        }
    }

    fun setAutoplay(enabled: Boolean) = container.settings.setAutoplay(enabled)

    fun showPlaylistPicker(track: Track) {
        _playlistPicker.value = track
    }

    fun dismissPlaylistPicker() {
        _playlistPicker.value = null
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            if (id == null) toast("Playlist name can't be empty") else toast("Playlist created")
        }
    }

    fun addToPlaylist(track: Track, playlistId: Long, playlistName: String) {
        viewModelScope.launch {
            val added = repository.addToPlaylist(track, playlistId)
            _playlistPicker.value = null
            toast(if (added) "Added to $playlistName" else "Already in $playlistName")
        }
    }

    fun createPlaylistAndAdd(name: String, track: Track) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            if (id == null) {
                toast("Playlist name can't be empty")
                return@launch
            }
            repository.addToPlaylist(track, id)
            _playlistPicker.value = null
            toast("Added to ${name.trim()}")
        }
    }

    fun removeFromPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            repository.removeFromPlaylist(playlistId, track.id)
            toast("Removed from playlist")
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            toast("Playlist deleted")
        }
    }

    fun playlistTracks(playlistId: Long): Flow<List<Track>> =
        repository.playlistTracks(playlistId)

    fun clearRecent() {
        viewModelScope.launch { repository.clearRecent() }
    }

    fun clearAll() {
        viewModelScope.launch {
            container.workManager.cancelAllWork()
            DownloadStatusStore.clear()
            container.playerManager.stop()
            OverlayService.stop(container.context)
            container.settings.reset()
            repository.clearAll()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(container.context, message, Toast.LENGTH_SHORT).show()
    }

    private fun friendly(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("429") ||
                message.contains("rate limit", ignoreCase = true) ||
                message.contains("recaptcha", ignoreCase = true) ->
                "YouTube is busy or rate-limiting requests. Try again in a minute."
            t is IOException -> "Network error. Check your connection and try again."
            else -> message.take(180).ifBlank { "Something went wrong." }
        }
    }
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
