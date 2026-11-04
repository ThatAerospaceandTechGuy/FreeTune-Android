package com.freetune.app.data.repo

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.freetune.app.data.db.AppDatabase
import com.freetune.app.data.db.PlaylistDao
import com.freetune.app.data.db.PlaylistEntity
import com.freetune.app.data.db.PlaylistTrackEntity
import com.freetune.app.data.db.TrackDao
import com.freetune.app.data.db.toEntity
import com.freetune.app.data.db.toTrack
import com.freetune.app.data.model.Playlist
import com.freetune.app.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
            dao.upsert(track.copy(lastPlayedAt = now).toEntity())
        } else {
            dao.upsert(
                existing.copy(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    thumbnailUrl = track.thumbnailUrl,
                    contentUri = track.contentUri ?: existing.contentUri,
                    lastPlayedAt = now
                )
            )
        }
    }

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
