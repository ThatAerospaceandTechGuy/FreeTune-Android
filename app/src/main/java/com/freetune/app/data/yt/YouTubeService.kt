package com.freetune.app.data.yt

import android.net.Uri
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import kotlin.math.abs

data class PlaybackSource(
    val url: String,
    val mimeType: String,
    val extension: String,
    val bitrate: Int
)

object YouTubeService {

    private val service = ServiceList.YouTube

    suspend fun search(query: String, limit: Int = 30): List<Track> =
        withContext(Dispatchers.IO) {
            val handler = service.searchQHFactory.fromQuery(query)
            val extractor = service.getSearchExtractor(handler)
            extractor.fetchPage()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0 }
                .mapNotNull { item -> item.toTrack() }
                .take(limit)
                .toList()
        }

    /**
     * Tracks to continue with when the queue ends. Uses YouTube's related streams and
     * falls back to a search for the current artist when related data is unavailable.
     */
    suspend fun related(
        videoId: String,
        seedTitle: String,
        seedArtist: String,
        limit: Int = 20
    ): List<Track> = withContext(Dispatchers.IO) {
        val direct = runCatching {
            val info = StreamInfo.getInfo(
                service,
                "https://www.youtube.com/watch?v=$videoId"
            )
            info.relatedStreams
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 30..1200 }
                .mapNotNull { item -> item.toTrack() }
                .toList()
        }.getOrDefault(emptyList())

        val fallback = if (direct.size < 5) {
            val query = listOf(seedArtist, seedTitle, "mix")
                .filter { it.isNotBlank() }
                .joinToString(" ")
            runCatching {
                search(query, limit).filter { it.durationMs in 30_000L..1_200_000L }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        (direct + fallback)
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun getPlaybackSource(
        videoId: String,
        quality: AudioQuality
    ): PlaybackSource = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(
            service,
            "https://www.youtube.com/watch?v=$videoId"
        )

        val all = info.audioStreams.filter { it.isUrl }
        if (all.isEmpty()) {
            throw IOException("No playable audio stream found for this video.")
        }

        val known = all.filter { it.averageBitrate > 0 }.ifEmpty { all }
        val chosen = when (quality) {
            AudioQuality.HIGH -> known.maxByOrNull { it.averageBitrate }
            AudioQuality.BALANCED ->
                known.minByOrNull { abs(it.averageBitrate - 128) }
            AudioQuality.DATA_SAVER -> known.minByOrNull { it.averageBitrate }
        } ?: throw IOException("No playable audio stream found for this video.")

        PlaybackSource(
            url = chosen.content,
            mimeType = chosen.format?.mimeType ?: "audio/mp4",
            extension = chosen.format?.suffix ?: "m4a",
            bitrate = chosen.averageBitrate
        )
    }

    suspend fun getDetails(videoId: String): Track? = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamInfo.getInfo(
                service,
                "https://www.youtube.com/watch?v=$videoId"
            )
            Track(
                id = videoId,
                title = info.name,
                artist = info.uploaderName.orEmpty().ifBlank { "Unknown artist" },
                durationMs = info.duration * 1000L,
                thumbnailUrl = info.thumbnails
                    .maxByOrNull { it.width.toLong() * it.height.toLong() }
                    ?.url
            )
        }.getOrNull()
    }

    private fun videoIdFromUrl(url: String): String? = runCatching {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v") ?: uri.lastPathSegment
    }.getOrNull()

    private fun StreamInfoItem.toTrack(): Track? {
        val id = videoIdFromUrl(url) ?: return null
        return Track(
            id = id,
            title = name,
            artist = uploaderName.orEmpty().ifBlank { "Unknown artist" },
            durationMs = duration * 1000L,
            thumbnailUrl = thumbnails
                .maxByOrNull { it.width.toLong() * it.height.toLong() }
                ?.url
        )
    }
}
