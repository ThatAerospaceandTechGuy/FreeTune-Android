package com.freetune.app.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String? = null,
    val localPath: String? = null,
    val liked: Boolean = false,
    val lastPlayedAt: Long = 0L
) {
    val isDownloaded: Boolean get() = !localPath.isNullOrBlank()
}

enum class AudioQuality(val label: String, val description: String) {
    HIGH("Best available", "Highest YouTube audio bitrate"),
    BALANCED("Balanced", "Around 128-160 kbps"),
    DATA_SAVER("Data saver", "Lowest bitrate, smallest files")
}
