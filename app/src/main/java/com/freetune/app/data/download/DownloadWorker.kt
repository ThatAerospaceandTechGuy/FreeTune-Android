package com.freetune.app.data.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.freetune.app.FreeTuneApp
import com.freetune.app.MainActivity
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Track
import com.freetune.app.data.repo.MusicRepository
import com.freetune.app.data.yt.AndroidDownloader
import com.freetune.app.data.yt.YouTubeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Unknown title"
        val artist = inputData.getString(KEY_ARTIST).orEmpty()
        val duration = inputData.getLong(KEY_DURATION, 0L)
        val thumbnail = inputData.getString(KEY_THUMBNAIL)
        val quality = runCatching {
            AudioQuality.valueOf(inputData.getString(KEY_QUALITY) ?: AudioQuality.HIGH.name)
        }.getOrDefault(AudioQuality.HIGH)

        val track = Track(
            id = id,
            title = title,
            artist = artist,
            durationMs = duration,
            thumbnailUrl = thumbnail
        )

        val app = applicationContext as FreeTuneApp
        val container = app.container
        val folder = MusicRepository.folderName(inputData.getString(KEY_FOLDER))
        val baseDir = MusicRepository.downloadDir(applicationContext)
        val dir = if (folder != null) File(baseDir, folder) else baseDir
        if (!dir.exists() && !dir.mkdirs()) {
            DownloadStatusStore.failed(track, "Could not create download folder")
            return Result.failure()
        }

        DownloadStatusStore.running(track)

        return try {
            val source = YouTubeService.getPlaybackSource(id, quality)
            val extension = source.extension.ifBlank { "m4a" }
            val displayName =
                if (artist.isNotBlank() && !title.startsWith(artist, ignoreCase = true)) {
                    "$artist - $title"
                } else {
                    title
                }
            val safeName = sanitize(displayName)
                .ifBlank { id }
                .take(80)
            val target = File(dir, "$safeName [$id].$extension")
            val partial = File(dir, "$safeName [$id].part")

            if (target.exists() && target.length() > 0L) {
                container.repository.saveDownload(track, target.absolutePath)
                DownloadStatusStore.done(id)
                return Result.success()
            }

            download(container.downloadHttp, source.url, partial) { percent ->
                DownloadStatusStore.progress(track, percent)
            }

            if (partial.length() <= 0L) {
                throw IOException("Downloaded file is empty")
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }

            container.repository.saveDownload(track, target.absolutePath)
            DownloadStatusStore.done(id)
            notify(title, "Saved to your downloads")
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.w("FreeTuneDownload", "Download failed for $id", t)
            DownloadStatusStore.failed(track, friendly(t))
            notify(title, "Download failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun download(
        client: OkHttpClient,
        url: String,
        target: File,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var existing = if (target.exists()) target.length() else 0L
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", AndroidDownloader.USER_AGENT)
        if (existing > 0L) {
            requestBuilder.header("Range", "bytes=$existing-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                // The partial file already covers the whole resource.
                onProgress(100)
                return@withContext
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response")

            val append = response.code == 206 && existing > 0L
            if (!append) existing = 0L
            val contentLength = body.contentLength()
            val total = if (contentLength > 0L) contentLength + existing else -1L
            var written = existing
            var lastPercent = -1

            java.io.FileOutputStream(target, append).buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) {
                            val percent = ((written * 100L) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun notify(title: String, text: String) {
        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, FreeTuneApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(title.hashCode(), notification)
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim()

    private fun friendly(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("429") || message.contains("rate limit", ignoreCase = true) ->
                "YouTube is rate-limiting downloads. Try again later."
            t is IOException -> "Network error. Check your connection."
            else -> message.take(160).ifBlank { "Download failed" }
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_DURATION = "duration"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_QUALITY = "quality"
        const val KEY_FOLDER = "folder"
        const val KEY_PROGRESS = "progress"
    }
}
