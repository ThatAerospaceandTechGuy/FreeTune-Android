package com.freetune.app.data.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.freetune.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    /** True when the source carried [mm:ss] timestamps. */
    val synced: Boolean,
    /** The track has no lyrics at all (LRCLIB marks instrumentals this way). */
    val instrumental: Boolean = false,
    /** Where the lyrics came from, for the small credit line on the player. */
    val source: String? = null
) {
    /** Index of the line that should be highlighted at [positionMs], or -1. */
    fun lineIndexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/**
 * Lyrics, in order of preference:
 *
 * 1. a `.lrc` file the user picked for this song,
 * 2. a `.lrc` stored next to a downloaded song,
 * 3. lyrics cached from LRCLIB for this song,
 * 4. LRCLIB, looked up by artist, title and duration.
 *
 * A local file always wins, so a hand-corrected file is never overwritten by the
 * online copy.
 */
class LyricsStore(context: Context, private val http: OkHttpClient) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("freetune_lyrics", Context.MODE_PRIVATE)

    fun hasSaved(trackId: String): Boolean = savedUri(trackId) != null

    /** True when a `.lrc` already sits next to this audio file. */
    fun hasSidecar(audioPath: String?): Boolean =
        audioPath?.takeIf { it.isNotBlank() }?.let { sidecar(it) } != null

    fun savedUri(trackId: String): String? = prefs.getString(manualKey(trackId), null)

    suspend fun save(trackId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching { readText(uri) }.getOrNull() ?: return@withContext false
        if (Parser.parse(text) == null) return@withContext false
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prefs.edit().putString(manualKey(trackId), uri.toString()).apply()
        true
    }

    suspend fun read(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        savedUri(track.id)?.let { saved ->
            val text = runCatching { readText(Uri.parse(saved)) }.getOrNull()
            if (text != null) {
                Parser.parse(text)?.copy(source = "your .lrc file")?.let { return@withContext it }
            }
        }

        track.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = sidecar(path)
            if (file != null) {
                val text = runCatching { file.readText() }.getOrNull()
                if (text != null) {
                    Parser.parse(text)?.copy(source = "your .lrc file")?.let { return@withContext it }
                }
            }
        }

        cached(track.id)?.let { return@withContext it }

        val fetched = fetch(track)
        if (fetched != null) cache(track.id, fetched)
        fetched
    }

    fun forget(trackId: String) {
        prefs.edit().remove(manualKey(trackId)).apply()
    }

    /**
     * Saves lyrics next to a downloaded audio file as `<name>.lrc`, which the
     * player picks up through the normal sidecar lookup, online or offline.
     */
    suspend fun saveSidecar(track: Track, audioPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val lyrics = fetch(track) ?: return@withContext false
            if (lyrics.instrumental || lyrics.lines.isEmpty()) return@withContext false
            val base = audioPath.substringBeforeLast('.', audioPath)
            val saved = runCatching { File("$base.lrc").writeText(serialize(lyrics)) }
                .isSuccess
            if (saved) cache(track.id, lyrics)
            saved
        }

    private fun fetch(track: Track): Lyrics? {
        val title = cleanTitle(track.title)
        if (title.isBlank()) return null
        val artist = cleanArtist(track.artist)
        val seconds = (track.durationMs / 1000L).takeIf { it in 1..3_600 }

        val direct = getRecord(
            url(
                "$BASE/api/get",
                "track_name" to title,
                "artist_name" to artist.ifBlank { "Unknown artist" },
                "duration" to seconds?.toString()
            )
        )
        if (direct != null) return direct
        return searchRecord(track, title, artist)
    }

    /** `/api/get` is exact; `/api/search` is the fallback when the duration differs. */
    private fun searchRecord(track: Track, title: String, artist: String): Lyrics? {
        val url = url(
            "$BASE/api/search",
            "track_name" to title,
            "artist_name" to artist.takeIf { it.isNotBlank() }
        )
        return runCatching {
            http.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val array = JSONArray(body)
                val target = track.durationMs / 1000L
                val best = (0 until array.length())
                    .mapNotNull { index -> runCatching { array.getJSONObject(index) }.getOrNull() }
                    .minByOrNull { record ->
                        val drift = abs(record.optLong("duration") - target)
                        // Prefer a synced result when the durations are close.
                        drift + if (record.optString("syncedLyrics").isBlank()) 20 else 0
                    }
                best?.let { parseRecord(it) }
            }
        }.getOrNull()
    }

    private fun getRecord(url: HttpUrl): Lyrics? = runCatching {
        http.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            parseRecord(JSONObject(body))
        }
    }.getOrNull()

    private fun request(url: HttpUrl): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

    private fun parseRecord(record: JSONObject): Lyrics? {
        if (record.optBoolean("instrumental")) {
            return Lyrics(emptyList(), synced = false, instrumental = true, source = SOURCE)
        }
        val text = record.optString("syncedLyrics").takeIf { it.isNotBlank() }
            ?: record.optString("plainLyrics").takeIf { it.isNotBlank() }
            ?: return null
        return Parser.parse(text)?.copy(source = SOURCE)
    }

    private fun cached(trackId: String): Lyrics? {
        val meta = prefs.getString(metaKey(trackId), null) ?: return null
        if (meta == META_INSTRUMENTAL) {
            return Lyrics(emptyList(), synced = false, instrumental = true, source = SOURCE)
        }
        val text = prefs.getString(textKey(trackId), null) ?: return null
        return Parser.parse(text)?.copy(source = SOURCE)
    }

    private fun cache(trackId: String, lyrics: Lyrics) {
        val editor = prefs.edit()
        when {
            lyrics.instrumental -> editor
                .putString(metaKey(trackId), META_INSTRUMENTAL)
                .remove(textKey(trackId))
            lyrics.synced -> editor
                .putString(metaKey(trackId), META_SYNCED)
                .putString(textKey(trackId), serialize(lyrics))
            else -> editor
                .putString(metaKey(trackId), META_PLAIN)
                .putString(textKey(trackId), serialize(lyrics))
        }
        editor.apply()
    }

    /** Rebuilds an LRC document from parsed lines so the cache is parseable again. */
    private fun serialize(lyrics: Lyrics): String = lyrics.lines.joinToString("\n") { line ->
        if (!lyrics.synced) {
            line.text
        } else {
            val minutes = line.timeMs / 60_000L
            val seconds = (line.timeMs % 60_000L) / 1_000L
            val centis = (line.timeMs % 1_000L) / 10L
            "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
        }
    }.take(MAX_CACHED_CHARS)

    /** Looks for the same file name with a `.lrc` extension. */
    private fun sidecar(path: String): File? {
        val base = path.substringBeforeLast('.', path)
        return listOf("$base.lrc", "$base.LRC")
            .map(::File)
            .firstOrNull { it.isFile && it.canRead() }
    }

    private fun readText(uri: Uri): String? {
        val stream = appContext.contentResolver.openInputStream(uri) ?: return null
        return stream.bufferedReader().use { reader ->
            val buffer = CharArray(MAX_CACHED_CHARS)
            val count = reader.read(buffer)
            if (count <= 0) null else String(buffer, 0, count)
        }
    }

    private fun url(base: String, vararg params: Pair<String, String?>): HttpUrl {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { (name, value) ->
            if (!value.isNullOrBlank()) builder.addQueryParameter(name, value)
        }
        return builder.build()
    }

    /** YouTube titles carry suffixes LRCLIB does not know about. */
    private fun cleanTitle(raw: String): String = raw
        .replace(
            Regex(
                "\\((?:official|lyrics?|audio|video|visuali[sz]er|hd|hq|explicit)[^)]*\\)",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex(
                "\\[(?:official|lyrics?|audio|video|visuali[sz]er|hd|hq|explicit)[^]]*]",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(Regex("\\s*\\|\\s*.*$"), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private fun cleanArtist(raw: String): String = raw
        .removeSuffix(" - Topic")
        .removeSuffix("VEVO")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    private fun manualKey(trackId: String) = "lyrics_$trackId"
    private fun metaKey(trackId: String) = "online_meta_$trackId"
    private fun textKey(trackId: String) = "online_text_$trackId"

    private companion object {
        const val BASE = "https://lrclib.net"
        const val SOURCE = "LRCLIB"
        const val USER_AGENT = "FreeTune/0.8.2 (Android music player)"
        const val MAX_CACHED_CHARS = 200_000
        const val META_SYNCED = "synced"
        const val META_PLAIN = "plain"
        const val META_INSTRUMENTAL = "instrumental"
    }
}

object Parser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val METADATA = Regex("""^\[[a-zA-Z#]+:.*]$""")

    fun parse(raw: String): Lyrics? {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val stamps = TIME_TAG.findAll(line).mapNotNull { toMillis(it) }.toList()
            val content = TIME_TAG.replace(line, "").trim()
            when {
                stamps.isNotEmpty() -> if (content.isNotEmpty()) {
                    stamps.forEach { timed += LyricLine(it, content) }
                }
                METADATA.matches(line) -> Unit
                else -> plain += line
            }
        }
        if (timed.isNotEmpty()) {
            timed.sortBy { it.timeMs }
            return Lyrics(timed, synced = true)
        }
        if (plain.isEmpty()) return null
        return Lyrics(
            plain.mapIndexed { index, text -> LyricLine(index.toLong(), text) },
            synced = false
        )
    }

    private fun toMillis(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        val millis = if (fraction.isEmpty()) {
            0L
        } else {
            fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
