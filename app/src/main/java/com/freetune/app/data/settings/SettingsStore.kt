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

    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQUALIZER_ENABLED, false))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(prefs.getInt(KEY_EQUALIZER_PRESET, -1))
    val equalizerPreset: StateFlow<Int> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(prefs.getString(KEY_EQUALIZER_BANDS, "").orEmpty())
    val equalizerBands: StateFlow<String> = _equalizerBands.asStateFlow()

    private val _bassBoost = MutableStateFlow(prefs.getInt(KEY_BASS_BOOST, 0).coerceIn(0, 100))
    val bassBoost: StateFlow<Int> = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow(prefs.getInt(KEY_VIRTUALIZER, 0).coerceIn(0, 100))
    val virtualizer: StateFlow<Int> = _virtualizer.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

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

    fun setEqualizerEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply()
        _equalizerEnabled.value = value
    }

    fun setEqualizerPreset(value: Int) {
        prefs.edit().putInt(KEY_EQUALIZER_PRESET, value).apply()
        _equalizerPreset.value = value
    }

    fun setEqualizerBands(value: String) {
        prefs.edit().putString(KEY_EQUALIZER_BANDS, value).apply()
        _equalizerBands.value = value
    }

    fun setBassBoost(value: Int) {
        val clamped = value.coerceIn(0, 100)
        prefs.edit().putInt(KEY_BASS_BOOST, clamped).apply()
        _bassBoost.value = clamped
    }

    fun setVirtualizer(value: Int) {
        val clamped = value.coerceIn(0, 100)
        prefs.edit().putInt(KEY_VIRTUALIZER, clamped).apply()
        _virtualizer.value = clamped
    }

    fun setDynamicColor(value: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        _dynamicColor.value = value
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
        _equalizerEnabled.value = false
        _equalizerPreset.value = -1
        _equalizerBands.value = ""
        _bassBoost.value = 0
        _virtualizer.value = 0
        _dynamicColor.value = true
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
        const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        const val KEY_BASS_BOOST = "bass_boost"
        const val KEY_VIRTUALIZER = "virtualizer"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val MAX_RECENT_SEARCHES = 12
    }
}
