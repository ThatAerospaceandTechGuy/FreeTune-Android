package com.freetune.app.data.repo

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.freetune.app.data.db.AppDatabase
import com.freetune.app.data.db.PlaylistDao
import com.freetune.app.data.db.PlaylistEntity
import com.freetune.app.data.db.PlaylistTrackEntity
import com.freetune.app.data.db.TrackDao
import com.freetune.app.data.db.TrackEntity
import com.freetune.app.data.db.toEntity
import com.freetune.app.data.db.toTrack
import com.freetune.app.data.model.Playlist
import com.freetune.app.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MusicRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val dao: TrackDao,
    private val playlistDao: PlaylistDao
) {

    val liked: Flow<List<Track>> =
        dao.observeLiked().map { entities -> entities.map { it.toTrack() } }

    val downloads: Flow<List<Track>> =
        dao.observeDownloads().map { entities -> entities.map { it.toTrack() } }

    val recent: Flow<List<Track>> =
        dao.observeRecent().map { entities -> entities.map { it.toTrack() } }

    val playlists: Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { rows ->
            rows.map { Playlist(id = it.id, name = it.name, trackCount = it.trackCount) }
        }

    fun playlistTracks(playlistId: Long): Flow<List<Track>> =
        playlistDao.observeTracks(playlistId).map { entities -> entities.map { it.toTrack() } }

    suspend fun toggleLike(track: Track) {
        val existing = dao.find(track.id)
        if (existing == null) {
            dao.upsert(track.copy(liked = true).toEntity())
        } else {
            dao.upsert(existing.copy(liked = !existing.liked))
        }
    }

    suspend fun markPlayed(track: Track) {
        val existing = dao.find(track.id)
        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.upsert(track.copy(lastPlayedAt = now, playCount = 1).toEntity())
        } else {
            dao.upsert(
                existing.copy(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    thumbnailUrl = track.thumbnailUrl,
                    contentUri = track.contentUri ?: existing.contentUri,
                    lastPlayedAt = now,
                    playCount = existing.playCount + 1
                )
            )
        }
    }

    suspend fun playedTracks(): List<Track> =
        dao.playedTracks().map { it.toTrack() }

    suspend fun saveDownload(track: Track, path: String) {
        val existing = dao.find(track.id)
        val base = existing ?: track.toEntity()
        val oldPath = existing?.localPath
        if (!oldPath.isNullOrBlank() && oldPath != path && isInsideDownloadDir(oldPath)) {
            runCatching { File(oldPath).delete() }
        }
        dao.upsert(
            base.copy(
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                thumbnailUrl = track.thumbnailUrl,
                localPath = path
            )
        )
    }

    suspend fun removeDownload(track: Track) {
        // Never delete a file the app did not create (e.g. an imported song from
        // the user's Music folder), only files inside Music/FreeTune.
        track.localPath
            ?.takeIf { isInsideDownloadDir(it) }
            ?.let { path -> runCatching { File(path).delete() } }
        dao.clearLocalPath(track.id)
    }

    suspend fun createPlaylist(name: String): Long? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        return playlistDao.create(
            PlaylistEntity(name = clean, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.delete(playlistId)

    suspend fun renamePlaylist(playlistId: Long, name: String): Boolean {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return false
        playlistDao.rename(playlistId, clean)
        return true
    }

    /** Copies a playlist and all of its track references, in the same order. */
    suspend fun duplicatePlaylist(playlistId: Long): Long? = database.withTransaction {
        val source = playlistDao.find(playlistId) ?: return@withTransaction null
        val newId = playlistDao.create(
            PlaylistEntity(
                name = "${source.name} copy".take(60),
                createdAt = System.currentTimeMillis()
            )
        )
        playlistDao.tracksOnce(playlistId).forEachIndexed { position, ref ->
            playlistDao.addTrack(
                PlaylistTrackEntity(
                    playlistId = newId,
                    trackId = ref.trackId,
                    position = position,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
        newId
    }

    /** Stores the current queue as a new playlist. Returns how many songs were added. */
    suspend fun saveQueueAsPlaylist(name: String, tracks: List<Track>): Int {
        val clean = name.trim().take(60)
        if (clean.isBlank() || tracks.isEmpty()) return 0
        return database.withTransaction {
            val playlistId = playlistDao.create(
                PlaylistEntity(name = clean, createdAt = System.currentTimeMillis())
            )
            var position = 0
            tracks.distinctBy { it.id }.forEach { track ->
                dao.upsert(dao.find(track.id) ?: track.toEntity())
                val inserted = playlistDao.addTrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = track.id,
                        position = position,
                        addedAt = System.currentTimeMillis()
                    )
                )
                if (inserted != -1L) position++
            }
            position
        }
    }

    suspend fun addToPlaylist(track: Track, playlistId: Long): Boolean {
        val playlist = playlistDao.find(playlistId) ?: return false
        val existing = dao.find(track.id) ?: track.toEntity()
        dao.upsert(existing)
        val position = playlistDao.nextPosition(playlist.id)
        val inserted = playlistDao.addTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                position = position,
                addedAt = System.currentTimeMillis()
            )
        )
        return inserted != -1L
    }

    /**
     * Imports songs that already exist on the device (MediaStore entries) into a
     * playlist. Returns how many songs were newly added.
     */
    suspend fun importTracks(playlistId: Long, tracks: List<Track>): Int =
        database.withTransaction {
            val playlist = playlistDao.find(playlistId) ?: return@withTransaction 0
            var position = playlistDao.nextPosition(playlist.id)
            var added = 0
            tracks.forEach { track ->
                val existing = dao.find(track.id)
                dao.upsert(
                    existing?.copy(
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                        thumbnailUrl = track.thumbnailUrl,
                        contentUri = track.contentUri ?: existing.contentUri
                    ) ?: track.toEntity()
                )
                val inserted = playlistDao.addTrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = track.id,
                        position = position,
                        addedAt = System.currentTimeMillis()
                    )
                )
                if (inserted != -1L) {
                    position++
                    added++
                }
            }
            added
        }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) {
        playlistDao.removeTrack(playlistId, trackId)
    }

    /** Persists a new song order after a drag-and-drop reorder. */
    suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<String>) {
        if (orderedTrackIds.isEmpty()) return
        database.withTransaction {
            orderedTrackIds.forEachIndexed { position, trackId ->
                playlistDao.setPosition(playlistId, trackId, position)
            }
        }
    }

    /** Serializes the library, playlists and play counts to a JSON document. */
    suspend fun exportLibraryJson(): String {
        val tracks = dao.allTracks()
        val playlists = playlistDao.allPlaylists()
        val refs = playlistDao.allPlaylistTracks()

        val trackArray = JSONArray()
        tracks.forEach { track ->
            trackArray.put(
                JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("durationMs", track.durationMs)
                    put("thumbnailUrl", track.thumbnailUrl ?: JSONObject.NULL)
                    put("localPath", track.localPath ?: JSONObject.NULL)
                    put("contentUri", track.contentUri ?: JSONObject.NULL)
                    put("liked", track.liked)
                    put("lastPlayedAt", track.lastPlayedAt)
                    put("addedAt", track.addedAt)
                    put("playCount", track.playCount)
                }
            )
        }

        val playlistArray = JSONArray()
        playlists.forEach { playlist ->
            val playlistTracks = JSONArray()
            refs.filter { it.playlistId == playlist.id }
                .sortedBy { it.position }
                .forEach { ref ->
                    playlistTracks.put(
                        JSONObject().apply {
                            put("trackId", ref.trackId)
                            put("position", ref.position)
                            put("addedAt", ref.addedAt)
                        }
                    )
                }
            playlistArray.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("createdAt", playlist.createdAt)
                    put("tracks", playlistTracks)
                }
            )
        }

        return JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("tracks", trackArray)
            put("playlists", playlistArray)
        }.toString(2)
    }

    /**
     * Replaces the library with a backup. Download paths are kept only when the file
     * still exists, so a restore from another device does not point at nothing.
     */
    suspend fun importLibraryJson(raw: String): Boolean = runCatching {
        val root = JSONObject(raw)
        if (root.optInt("version", 0) != 1) return false
        val tracks = root.optJSONArray("tracks") ?: JSONArray()
        val playlists = root.optJSONArray("playlists") ?: JSONArray()

        database.withTransaction {
            playlistDao.clearAll()
            dao.clearAll()

            for (index in 0 until tracks.length()) {
                val item = tracks.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val localPath = item.textOrNull("localPath")
                    ?.takeIf { path -> File(path).exists() }
                dao.upsert(
                    TrackEntity(
                        id = id,
                        title = item.optString("title").ifBlank { "Unknown title" },
                        artist = item.optString("artist").ifBlank { "Unknown artist" },
                        durationMs = item.optLong("durationMs"),
                        thumbnailUrl = item.textOrNull("thumbnailUrl"),
                        localPath = localPath,
                        contentUri = item.textOrNull("contentUri"),
                        liked = item.optBoolean("liked"),
                        lastPlayedAt = item.optLong("lastPlayedAt"),
                        addedAt = item.optLong("addedAt", System.currentTimeMillis()),
                        playCount = item.optInt("playCount")
                    )
                )
            }

            for (index in 0 until playlists.length()) {
                val item = playlists.optJSONObject(index) ?: continue
                val name = item.optString("name").trim().take(60)
                    .ifBlank { "Imported playlist" }
                val playlistId = playlistDao.create(
                    PlaylistEntity(
                        name = name,
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                val playlistTracks = item.optJSONArray("tracks") ?: JSONArray()
                for (refIndex in 0 until playlistTracks.length()) {
                    val ref = playlistTracks.optJSONObject(refIndex) ?: continue
                    val trackId = ref.optString("trackId")
                    if (trackId.isBlank() || dao.find(trackId) == null) continue
                    playlistDao.addTrack(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = trackId,
                            position = ref.optInt("position", refIndex),
                            addedAt = ref.optLong("addedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
        true
    }.getOrDefault(false)

    suspend fun clearRecent() = dao.clearRecent()

    suspend fun clearAll() {
        playlistDao.clearAll()
        dao.clearAll()
        downloadDir(context).deleteRecursively()
    }

    private fun isInsideDownloadDir(path: String): Boolean = runCatching {
        val root = downloadDir(context).canonicalPath + File.separator
        File(path).canonicalPath.startsWith(root)
    }.getOrDefault(false)

    private fun JSONObject.textOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        fun downloadDir(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "music")
            return File(base, "FreeTune")
        }

        fun folderName(raw: String?): String? {
            val clean = raw
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                ?.trim()
                ?.take(60)
                .orEmpty()
            return clean.ifBlank { null }
        }
    }
}
