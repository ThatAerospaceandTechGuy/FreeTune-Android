package com.freetune.app.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.freetune.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A folder on the device that contains playable audio (Music, Download, ...). */
data class LocalFolder(
    val label: String,
    val path: String,
    val songs: List<Track>
)

data class LocalSongsState(
    val loading: Boolean = false,
    val scanned: Boolean = false,
    val folders: List<LocalFolder> = emptyList(),
    val error: String? = null
) {
    val total: Int get() = folders.sumOf { it.songs.size }
}

/**
 * Finds audio that is already on the device through MediaStore: the OS Music folder,
 * the Download folder and files other apps saved (WhatsApp, recorders, other players).
 */
object LocalAudioScanner {

    private const val MIN_DURATION_MS = 10_000L
    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun scan(context: Context): List<LocalFolder> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM
        )

        val found = ArrayList<Pair<Track, String>>()
        val seen = HashSet<String>()

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val ringtoneColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
            val notificationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)
            val alarmColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationColumn)
                if (duration < MIN_DURATION_MS) continue
                if (cursor.getInt(ringtoneColumn) == 1 ||
                    cursor.getInt(notificationColumn) == 1 ||
                    cursor.getInt(alarmColumn) == 1
                ) {
                    continue
                }

                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                if (!seen.add(contentUri.toString())) continue

                val path = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumColumn)
                val rawTitle = cursor.getString(titleColumn)
                val displayName = cursor.getString(nameColumn)
                val artist = cursor.getString(artistColumn)

                val track = Track(
                    id = Track.LOCAL_PREFIX + id,
                    title = rawTitle?.takeIf { it.isNotBlank() }
                        ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: "Unknown title",
                    artist = artist
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: "Unknown artist",
                    durationMs = duration,
                    thumbnailUrl = if (albumId > 0L) {
                        ContentUris.withAppendedId(albumArtBase, albumId).toString()
                    } else {
                        null
                    },
                    contentUri = contentUri.toString()
                )
                found.add(track to folderOf(path))
            }
        }

        found
            .groupBy { it.second }
            .map { (folder, items) ->
                LocalFolder(
                    label = folder.substringAfterLast('/').ifBlank { folder },
                    path = folder,
                    songs = items.map { it.first }
                )
            }
            .sortedWith(
                compareBy<LocalFolder> { folderRank(it.label) }
                    .thenByDescending { it.songs.size }
                    .thenBy { it.label.lowercase() }
            )
    }

    private fun folderOf(path: String?): String {
        if (path.isNullOrBlank()) return "Other"
        val parent = File(path).parent ?: return "Other"
        val root = Environment.getExternalStorageDirectory()?.absolutePath
        val relative = if (root != null && parent.startsWith(root)) {
            parent.removePrefix(root).trim('/')
        } else {
            File(parent).name
        }
        return relative.ifBlank { "Music" }
    }

    private fun folderRank(label: String): Int {
        val lower = label.lowercase()
        return when {
            lower == "music" -> 0
            lower == "download" || lower == "downloads" -> 1
            else -> 2
        }
    }
}
