package com.freetune.app.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String? = null,
    val localPath: String? = null,
    val contentUri: String? = null,
    val liked: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0
) {
    /** True when FreeTune downloaded this song into its own folder. */
    val isDownloaded: Boolean get() = !localPath.isNullOrBlank()

    /** True when the song lives on the device (imported from a Music/Download folder). */
    val isLocalFile: Boolean get() = !contentUri.isNullOrBlank() || id.startsWith(LOCAL_PREFIX)

    /** Playable without reaching YouTube. */
    val isAvailableOffline: Boolean get() = isDownloaded || !contentUri.isNullOrBlank()

    companion object {
        const val LOCAL_PREFIX = "local:"
    }
}

enum class AudioQuality(val label: String, val description: String) {
    HIGH("Best available", "Highest YouTube audio bitrate"),
    BALANCED("Balanced", "Around 128-160 kbps"),
    DATA_SAVER("Data saver", "Lowest bitrate, smallest files")
}
