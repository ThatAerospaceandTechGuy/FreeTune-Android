package com.freetune.app.data.download

import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DownloadState { QUEUED, RUNNING, FAILED }

data class DownloadStatus(
    val track: Track,
    val progress: Int = 0,
    val state: DownloadState = DownloadState.QUEUED,
    val message: String? = null
)

object DownloadStatusStore {

    private val _items = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val items: StateFlow<Map<String, DownloadStatus>> = _items.asStateFlow()

    fun queued(track: Track) {
        _items.update {
            it + (track.id to DownloadStatus(track = track, state = DownloadState.QUEUED))
        }
    }

    fun running(track: Track) {
        _items.update {
            it + (track.id to DownloadStatus(track = track, state = DownloadState.RUNNING))
        }
    }

    fun progress(track: Track, progress: Int) {
        _items.update { current ->
            val existing = current[track.id]
            if (existing == null || existing.state == DownloadState.FAILED) {
                current
            } else {
                current + (track.id to existing.copy(
                    progress = progress.coerceIn(0, 100),
                    state = DownloadState.RUNNING
                ))
            }
        }
    }

    fun failed(track: Track, message: String) {
        _items.update {
            it + (track.id to DownloadStatus(
                track = track,
                progress = 0,
                state = DownloadState.FAILED,
                message = message
            ))
        }
    }

    fun done(id: String) {
        _items.update { it - id }
    }

    fun dismiss(id: String) {
        _items.update { it - id }
    }

    fun clearFinished() {
        _items.update { map ->
            map.filterValues { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
        }
    }

    fun clear() {
        _items.value = emptyMap()
    }
}

object Downloads {

    private const val TAG = "freetune_download"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_DURATION = "duration"
    private const val KEY_THUMBNAIL = "thumbnail"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FOLDER = "folder"

    fun enqueue(
        context: android.content.Context,
        track: Track,
        quality: AudioQuality,
        wifiOnly: Boolean,
        replace: Boolean = false,
        folder: String? = null
    ) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) androidx.work.NetworkType.UNMETERED
                else androidx.work.NetworkType.CONNECTED
            )
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    KEY_ID to track.id,
                    KEY_TITLE to track.title,
                    KEY_ARTIST to track.artist,
                    KEY_DURATION to track.durationMs,
                    KEY_THUMBNAIL to track.thumbnailUrl,
                    KEY_QUALITY to quality.name,
                    KEY_FOLDER to folder
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                20,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .addTag(TAG)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download-${track.id}",
            if (replace) androidx.work.ExistingWorkPolicy.REPLACE
            else androidx.work.ExistingWorkPolicy.KEEP,
            request
        )

        DownloadStatusStore.queued(track)
    }
}
