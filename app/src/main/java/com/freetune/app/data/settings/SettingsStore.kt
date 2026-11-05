package com.freetune.app.data.settings

import android.content.Context
import com.freetune.app.data.model.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("freetune_settings", Context.MODE_PRIVATE)

    private val _quality = MutableStateFlow(loadQuality())
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _floatingControls = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_CONTROLS, false))
    val floatingControls: StateFlow<Boolean> = _floatingControls.asStateFlow()

    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _incognito = MutableStateFlow(prefs.getBoolean(KEY_INCOGNITO, false))
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()

    private val _speed = MutableStateFlow(loadSpeed())
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun setQuality(value: AudioQuality) {
        prefs.edit().putString(KEY_QUALITY, value.name).apply()
        _quality.value = value
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    fun setFloatingControls(value: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_CONTROLS, value).apply()
        _floatingControls.value = value
    }

    fun setAutoplay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        _autoplay.value = value
    }

    fun setIncognito(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO, value).apply()
        _incognito.value = value
    }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(0.5f, 2f)
        prefs.edit().putFloat(KEY_SPEED, clamped).apply()
        _speed.value = clamped
    }

    /** Keeps the last [MAX_RECENT_SEARCHES] unique queries, newest first. */
    fun addRecentSearch(query: String) {
        val clean = query.trim().take(80)
        if (clean.isBlank()) return
        val next = (listOf(clean) + _recentSearches.value.filterNot {
            it.equals(clean, ignoreCase = true)
        }).take(MAX_RECENT_SEARCHES)
        prefs.edit()
            .putString(KEY_RECENT_SEARCHES, JSONArray(next).toString())
            .apply()
        _recentSearches.value = next
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
        _recentSearches.value = emptyList()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _quality.value = AudioQuality.HIGH
        _wifiOnly.value = false
        _floatingControls.value = false
        _autoplay.value = true
        _incognito.value = false
        _speed.value = 1f
        _recentSearches.value = emptyList()
    }

    private fun loadSpeed(): Float = prefs.getFloat(KEY_SPEED, 1f).coerceIn(0.5f, 2f)

    private fun loadSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.take(MAX_RECENT_SEARCHES)
        }.getOrDefault(emptyList())
    }

    private fun loadQuality(): AudioQuality {
        val saved = prefs.getString(KEY_QUALITY, AudioQuality.HIGH.name)
        return runCatching { AudioQuality.valueOf(saved ?: AudioQuality.HIGH.name) }
            .getOrDefault(AudioQuality.HIGH)
    }

    private companion object {
        const val KEY_QUALITY = "audio_quality"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_FLOATING_CONTROLS = "floating_controls"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_INCOGNITO = "incognito"
        const val KEY_SPEED = "playback_speed"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val MAX_RECENT_SEARCHES = 12
    }
}
