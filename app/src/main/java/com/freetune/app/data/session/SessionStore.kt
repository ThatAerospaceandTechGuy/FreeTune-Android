package com.freetune.app.data.session

import android.content.Context
import com.freetune.app.data.model.Track
import org.json.JSONArray
import org.json.JSONObject

data class SavedSession(
    val queue: List<Track>,
    val index: Int,
    val positionMs: Long
)

/**
 * Small, dependency-free snapshot of what was playing. It lives in its own
 * SharedPreferences file so "erase all data" can clear it with everything else.
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("freetune_session", Context.MODE_PRIVATE)

    fun save(queue: List<Track>, index: Int, positionMs: Long) {
        if (queue.isEmpty()) {
            clear()
            return
        }
        val trimmed = queue.take(MAX_TRACKS)
        val safeIndex = index.coerceIn(0, trimmed.lastIndex)
        val array = JSONArray()
        trimmed.forEach { track -> array.put(track.toJson()) }
        prefs.edit()
            .putString(KEY_QUEUE, array.toString())
            .putInt(KEY_INDEX, safeIndex)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun load(): SavedSession? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val parsed = runCatching {
            val array = JSONArray(raw)
            val tracks = ArrayList<Track>(array.length())
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                fromJson(json)?.let { tracks.add(it) }
            }
            tracks
        }.getOrNull()

        if (parsed.isNullOrEmpty()) return null
        return SavedSession(
            queue = parsed,
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, parsed.lastIndex),
            positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun Track.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("durationMs", durationMs)
        put("thumbnailUrl", thumbnailUrl)
        put("localPath", localPath)
        put("contentUri", contentUri)
    }

    private fun fromJson(json: JSONObject): Track? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = json.optString("title").takeIf { it.isNotBlank() } ?: "Unknown title",
            artist = json.optString("artist").takeIf { it.isNotBlank() } ?: "Unknown artist",
            durationMs = json.optLong("durationMs", 0L),
            thumbnailUrl = json.stringOrNull("thumbnailUrl"),
            localPath = json.stringOrNull("localPath"),
            contentUri = json.stringOrNull("contentUri")
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"

        /** Keeps the snapshot small; autoplay can extend the queue without limit. */
        const val MAX_TRACKS = 120
    }
}
