package com.freetune.app.data.settings

import android.content.Context
import com.freetune.app.data.model.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun reset() {
        prefs.edit().clear().apply()
        _quality.value = AudioQuality.HIGH
        _wifiOnly.value = false
        _floatingControls.value = false
        _autoplay.value = true
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
    }
}
