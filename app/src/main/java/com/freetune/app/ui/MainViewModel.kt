package com.freetune.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freetune.app.data.AppContainer
import com.freetune.app.data.download.DownloadState
import com.freetune.app.data.download.DownloadStatus
import com.freetune.app.data.download.DownloadStatusStore
import com.freetune.app.data.download.Downloads
import com.freetune.app.data.local.LocalAudioScanner
import com.freetune.app.data.lyrics.Lyrics
import com.freetune.app.data.local.LocalSongsState
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Playlist
import com.freetune.app.data.model.Track
import com.freetune.app.data.yt.YouTubeService
import com.freetune.app.playback.EqualizerUiState
import com.freetune.app.playback.OverlayService
import com.freetune.app.playback.PlayerUiState
import com.freetune.app.playback.RepeatMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class ArtistUiState(
    val name: String = "",
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class ListeningStats(
    val totalPlays: Int = 0,
    val totalMinutes: Long = 0,
    val topTracks: List<Track> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList()
)

data class LyricsUiState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    /** True when a .lrc file is paired with this song, so it can be removed. */
    val manual: Boolean = false
)

/** Progress of the one-tap lyrics pass over downloaded songs. */
data class LyricsBackfillState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val saved: Int = 0,
    val missing: Int = 0,
    val finished: Boolean = false
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
    val incognito: StateFlow<Boolean> = container.settings.incognito
    val speed: StateFlow<Float> = container.settings.speed
    val equalizer: StateFlow<EqualizerUiState> = container.playerManager.effects.state
    val dynamicColor: StateFlow<Boolean> = container.settings.dynamicColor
    val recentSearches: StateFlow<List<String>> = container.settings.recentSearches
    val downloadStatuses: StateFlow<Map<String, DownloadStatus>> = DownloadStatusStore.items

    private val _localSongs = MutableStateFlow(LocalSongsState())
    val localSongs: StateFlow<LocalSongsState> = _localSongs.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _discover = MutableStateFlow(SearchUiState(query = DEFAULT_DISCOVER))
    val discover: StateFlow<SearchUiState> = _discover.asStateFlow()

    private val _playlistPicker = MutableStateFlow<Track?>(null)
    val playlistPicker: StateFlow<Track?> = _playlistPicker.asStateFlow()

    private val _playlistDownload = MutableStateFlow<PlaylistDownloadRequest?>(null)
    val playlistDownload: StateFlow<PlaylistDownloadRequest?> = _playlistDownload.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

    private val _lyricsBackfill = MutableStateFlow(LyricsBackfillState())
    val lyricsBackfill: StateFlow<LyricsBackfillState> = _lyricsBackfill.asStateFlow()

    private val _artist = MutableStateFlow(ArtistUiState())
    val artist: StateFlow<ArtistUiState> = _artist.asStateFlow()

    private val _stats = MutableStateFlow(ListeningStats())
    val stats: StateFlow<ListeningStats> = _stats.asStateFlow()

    private val _importingPlaylist = MutableStateFlow(false)
    val importingPlaylist: StateFlow<Boolean> = _importingPlaylist.asStateFlow()

    /** Emits once per manual skip so the UI can offer an Undo action. */
    private val _skipUndo = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipUndo: SharedFlow<Unit> = _skipUndo.asSharedFlow()

    private var searchJob: Job? = null
    private var localScanJob: Job? = null
    private var lyricsJob: Job? = null
    private var lyricsBackfillJob: Job? = null
    private var artistJob: Job? = null

    init {
        // Lyrics are looked up once per track change, not on every position tick.
        viewModelScope.launch {
            container.playerManager.state
                .map { it.current }
                .distinctUntilChanged()
                .collect { track -> loadLyrics(track) }
        }
    }

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
        searchNow(_search.value.query)
    }

    /** Runs a search immediately, used by the keyboard action and history chips. */
    fun searchNow(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        _search.update { it.copy(query = clean) }
        container.settings.addRecentSearch(clean)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(clean, _search)
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
            viewModelScope.launch { loadDailyMix() }
        }
    }

    /**
     * Builds the Home mix from the artists behind recent and liked songs. Falls back
     * to the default search when there is no history yet or the mix comes up empty.
     */
    private suspend fun loadDailyMix() {
        val seeds = (repository.recent.first() + repository.liked.first())
            .map { it.artist }
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        if (seeds.isEmpty()) {
            loadDiscover(DEFAULT_DISCOVER)
            return
        }

        _discover.update {
            it.copy(query = DAILY_MIX_TITLE, loading = true, hasSearched = true, error = null)
        }
        val mix = seeds
            .flatMap { artist ->
                runCatching { YouTubeService.search("$artist mix", 10) }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.id }
            .shuffled()
            .take(30)

        if (mix.isEmpty()) {
            loadDiscover(DEFAULT_DISCOVER)
            return
        }
        _discover.update {
            it.copy(results = mix, loading = false, hasSearched = true, error = null)
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
        toast(if (shuffle) "Shuffling ${playlist.name}" else "Playing ${playlist.name}")
    }

    fun togglePlayPause() = container.playerManager.togglePlayPause()

    fun next() {
        if (container.playerManager.next()) _skipUndo.tryEmit(Unit)
    }

    fun previous() = container.playerManager.previous()

    fun undoSkip() {
        container.playerManager.undoSkip()
    }

    fun playNext(track: Track) {
        val queued = container.playerManager.playNext(track)
        toast(if (queued) "Playing next" else "Already playing")
    }

    fun addToQueue(track: Track) {
        val queued = container.playerManager.addToQueue(track)
        toast(if (queued) "Added to queue" else "Already playing")
    }
    fun seekTo(positionMs: Long) = container.playerManager.seekTo(positionMs)

    fun toggleLike(track: Track) {
        viewModelScope.launch { repository.toggleLike(track) }
    }

    fun download(track: Track, replace: Boolean = false, folder: String? = null) {
        if (track.contentUri != null) {
            toast("This song is already on this device")
            return
        }
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
            track.contentUri == null &&
                track.id !in activeIds &&
                (!skipDownloaded || !track.isDownloaded)
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

    fun setIncognito(enabled: Boolean) = container.settings.setIncognito(enabled)

    fun cycleRepeatMode() {
        val mode = container.playerManager.cycleRepeatMode()
        toast(
            when (mode) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> "Repeat all"
                RepeatMode.ONE -> "Repeat this song"
            }
        )
    }

    fun setSleepTimer(minutes: Int) {
        container.playerManager.setSleepTimer(minutes)
        toast("Sleep timer: $minutes min")
    }

    fun setSleepAtEndOfTrack() {
        container.playerManager.setSleepAtEndOfTrack()
        toast("Stopping after this song")
    }

    fun cancelSleepTimer() {
        container.playerManager.cancelSleepTimer()
        toast("Sleep timer off")
    }

    fun jumpToQueue(position: Int) = container.playerManager.jumpToQueue(position)

    fun removeFromQueue(position: Int) {
        if (container.playerManager.removeFromQueue(position)) {
            toast("Removed from queue")
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        container.playerManager.moveQueueItem(from, to)
    }

    fun clearUpcoming() {
        val removed = container.playerManager.clearUpcoming()
        if (removed > 0) toast("Cleared $removed upcoming")
    }

    /** Saves the live queue as a normal playlist, in queue order. */
    fun saveQueueAsPlaylist(name: String) {
        val tracks = playerState.value.queue
        if (tracks.isEmpty()) {
            toast("Queue is empty")
            return
        }
        viewModelScope.launch {
            val added = repository.saveQueueAsPlaylist(name, tracks)
            toast(
                if (added > 0) "Saved $added songs to ${name.trim().take(60)}"
                else "Couldn't save the queue"
            )
        }
    }

    /** Fetches a public playlist and stores its songs as a new local playlist. */
    fun importPlaylist(url: String) {
        if (_importingPlaylist.value) return
        if (url.isBlank()) {
            toast("Paste a playlist link first")
            return
        }
        viewModelScope.launch {
            _importingPlaylist.value = true
            toast("Importing playlist…")
            try {
                val imported = YouTubeService.importPlaylist(url)
                val added = repository.saveQueueAsPlaylist(imported.name, imported.tracks)
                toast(
                    if (added > 0) "Imported $added songs into ${imported.name}"
                    else "Couldn't import that playlist"
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                toast(friendly(t))
            } finally {
                _importingPlaylist.value = false
            }
        }
    }

    fun setSpeed(value: Float) = container.playerManager.setSpeed(value)

    fun setEqualizerEnabled(value: Boolean) =
        container.playerManager.effects.setEnabled(value)

    fun setEqualizerPreset(index: Int) =
        container.playerManager.effects.setPreset(index)

    fun setEqualizerBand(index: Int, levelMb: Int) =
        container.playerManager.effects.setBand(index, levelMb)

    fun setBassBoost(percent: Int) =
        container.playerManager.effects.setBassBoost(percent)

    fun setVirtualizer(percent: Int) =
        container.playerManager.effects.setVirtualizer(percent)

    fun setDynamicColor(enabled: Boolean) = container.settings.setDynamicColor(enabled)

    fun clearRecentSearches() = container.settings.clearRecentSearches()

    /** Pairs the current song with a `.lrc` file the user picks. */
    fun attachLyrics(uri: Uri) {
        val track = container.playerManager.state.value.current ?: return
        viewModelScope.launch {
            val saved = container.lyrics.save(track.id, uri)
            if (!saved) {
                toast("That file has no lyrics in it")
                return@launch
            }
            loadLyrics(track)
            toast("Lyrics added")
        }
    }

    fun removeLyrics() {
        val track = container.playerManager.state.value.current ?: return
        container.lyrics.forget(track.id)
        loadLyrics(track)
        toast("Saved .lrc file detached")
    }

    /**
     * Loads lyrics for [track]: a paired .lrc file first, then the cached online
     * copy, then an LRCLIB lookup by artist, title and duration.
     */
    private fun loadLyrics(track: Track?) {
        lyricsJob?.cancel()
        if (track == null) {
            _lyrics.value = LyricsUiState()
            return
        }
        val manual = container.lyrics.hasSaved(track.id)
        _lyrics.value = LyricsUiState(trackId = track.id, loading = true, manual = manual)
        lyricsJob = viewModelScope.launch {
            val found = runCatching { container.lyrics.read(track) }.getOrNull()
            _lyrics.value = LyricsUiState(
                trackId = track.id,
                loading = false,
                lyrics = found,
                manual = manual
            )
        }
    }

    /**
     * Fetches and saves a `.lrc` next to every downloaded song that does not have
     * one yet. Requests go out one at a time with a short gap, as LRCLIB asks.
     */
    fun backfillLyrics() {
        if (_lyricsBackfill.value.running) return
        val tracks = downloads.value.filter { !it.localPath.isNullOrBlank() }
        if (tracks.isEmpty()) {
            toast("Nothing downloaded yet")
            return
        }
        _lyricsBackfill.value = LyricsBackfillState(running = true, total = tracks.size)
        lyricsBackfillJob = viewModelScope.launch {
            var done = 0
            var saved = 0
            var missing = 0
            for ((position, track) in tracks.withIndex()) {
                val path = track.localPath ?: continue
                val hasLyrics = container.lyrics.hasSidecar(path)
                if (!hasLyrics) {
                    val ok = runCatching { container.lyrics.saveSidecar(track, path) }
                        .getOrDefault(false)
                    if (ok) saved++ else missing++
                    if (track.id == container.playerManager.state.value.current?.id) {
                        loadLyrics(track)
                    }
                    // Space the lookups out instead of firing them all at once.
                    if (position < tracks.lastIndex) delay(350)
                }
                done++
                _lyricsBackfill.value = LyricsBackfillState(
                    running = true,
                    done = done,
                    total = tracks.size,
                    saved = saved,
                    missing = missing
                )
            }
            _lyricsBackfill.value = LyricsBackfillState(
                running = false,
                done = tracks.size,
                total = tracks.size,
                saved = saved,
                missing = missing,
                finished = true
            )
            val noun = if (saved == 1) "song" else "songs"
            toast(if (saved == 0) "No new lyrics found" else "Lyrics saved for $saved $noun")
        }
    }

    fun startRadio(track: Track) {
        container.playerManager.startRadio(track)
        toast("Starting radio")
    }

    /**
     * Builds an artist page from what is already known locally (likes, recent,
     * downloads) plus a YouTube search for that artist's songs.
     */
    fun loadArtist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        artistJob?.cancel()
        _artist.value = ArtistUiState(name = clean, loading = true)
        artistJob = viewModelScope.launch {
            try {
                val known = (liked.value + recent.value + downloads.value)
                    .filter { it.artist.equals(clean, ignoreCase = true) }
                val found = YouTubeService.search("$clean songs", 40)
                    .filter { it.artist.contains(clean, ignoreCase = true) }
                val fallback = if (found.size < 5) {
                    YouTubeService.search(clean, 40)
                } else {
                    emptyList()
                }
                val tracks = (known + found + fallback).distinctBy { it.id }
                _artist.value = ArtistUiState(
                    name = clean,
                    tracks = tracks,
                    loading = false,
                    error = null
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _artist.value = ArtistUiState(
                    name = clean,
                    loading = false,
                    error = friendly(t)
                )
            }
        }
    }

    /** Computes local-only listening statistics from the play counters. */
    fun loadStats() {
        viewModelScope.launch {
            val played = repository.playedTracks()
            val topTracks = played.sortedByDescending { it.playCount }.take(10)
            val topArtists = played
                .groupBy { it.artist }
                .map { (artist, tracks) -> artist to tracks.sumOf { it.playCount } }
                .sortedByDescending { it.second }
                .take(10)
            _stats.value = ListeningStats(
                totalPlays = played.sumOf { it.playCount },
                totalMinutes = played.sumOf { it.durationMs * it.playCount } / 60_000L,
                topTracks = topTracks,
                topArtists = topArtists
            )
        }
    }

    fun exportLibrary(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = repository.exportLibraryJson()
                withContext(Dispatchers.IO) {
                    container.context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the destination file")
                }
            }.isSuccess
            toast(if (ok) "Library exported" else "Couldn't export the library")
        }
    }

    fun importLibrary(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val raw = withContext(Dispatchers.IO) {
                    container.context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Could not read the selected file")
                }
                repository.importLibraryJson(raw)
            }.getOrDefault(false)
            toast(if (ok) "Library imported" else "Couldn't import that backup")
        }
    }

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

    /** Saves a new song order after a long-press drag inside a playlist. */
    fun reorderPlaylist(playlistId: Long, ordered: List<Track>) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, ordered.map { it.id })
        }
    }

    /** Adds songs that already exist on the device to a playlist. */
    fun importLocalTracks(playlist: Playlist, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val added = repository.importTracks(playlist.id, tracks)
            val skipped = tracks.size - added
            val noun = if (added == 1) "song" else "songs"
            val message = buildString {
                append("Added $added $noun to ${playlist.name}")
                if (skipped > 0) append(" · $skipped already there")
            }
            toast(message)
        }
    }

    fun scanLocalSongs(force: Boolean = false) {
        val current = _localSongs.value
        if (current.loading) return
        if (current.scanned && !force) return
        localScanJob?.cancel()
        localScanJob = viewModelScope.launch {
            _localSongs.update { it.copy(loading = true, error = null) }
            try {
                val folders = LocalAudioScanner.scan(container.context)
                _localSongs.value = LocalSongsState(
                    loading = false,
                    scanned = true,
                    folders = folders
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _localSongs.value = LocalSongsState(
                    loading = false,
                    scanned = true,
                    error = t.message?.take(160).orEmpty().ifBlank {
                        "Could not read songs from this device."
                    }
                )
            }
        }
    }

    fun resetLocalSongs() {
        localScanJob?.cancel()
        localScanJob = null
        _localSongs.value = LocalSongsState()
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            toast("Playlist deleted")
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            val renamed = repository.renamePlaylist(playlistId, name)
            toast(if (renamed) "Playlist renamed" else "Playlist name can't be empty")
        }
    }

    fun duplicatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val id = repository.duplicatePlaylist(playlist.id)
            toast(if (id != null) "Playlist duplicated" else "Couldn't duplicate playlist")
        }
    }

    fun playlistTracks(playlistId: Long): Flow<List<Track>> =
        repository.playlistTracks(playlistId)

    fun clearRecent() {
        viewModelScope.launch { repository.clearRecent() }
    }

    fun clearAll() {
        viewModelScope.launch {
            resetLocalSongs()
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

    private companion object {
        const val DEFAULT_DISCOVER = "Top songs 2026"
        const val DAILY_MIX_TITLE = "Made for you"
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
