package com.freetune.app.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.freetune.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val centerHz: Int,
    val levelMb: Int,
    val minMb: Int,
    val maxMb: Int
)

data class EqualizerUiState(
    val supported: Boolean = true,
    val attached: Boolean = false,
    val enabled: Boolean = false,
    val preset: Int = -1,
    val presets: List<String> = emptyList(),
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val error: String? = null
)

/**
 * Owns the platform audio effects for ExoPlayer's current audio session. Effects are
 * optional: an unsupported phone simply reports supported=false instead of crashing.
 */
class AudioEffectController(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId = 0

    private val _state = MutableStateFlow(
        EqualizerUiState(
            enabled = settings.equalizerEnabled.value,
            preset = settings.equalizerPreset.value,
            bassBoost = settings.bassBoost.value,
            virtualizer = settings.virtualizer.value
        )
    )
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    /** Binds to a new ExoPlayer audio session. Safe to call repeatedly. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == sessionId && equalizer != null) return
        releaseEffects()
        sessionId = audioSessionId
        var failure: String? = null

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            if (!eq.hasControl()) {
                error("The equalizer could not take control of this audio session.")
            }
            eq.enabled = settings.equalizerEnabled.value
            val saved = savedLevels()
            val preset = settings.equalizerPreset.value
            if (preset in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(preset.toShort())
            } else if (saved.size == eq.numberOfBands.toInt()) {
                saved.forEachIndexed { index, level ->
                    eq.setBandLevel(index.toShort(), level.toShort())
                }
            }
        }.onFailure {
            failure = it.message ?: it.javaClass.simpleName
        }

        if (failure != null) {
            releaseEffects()
            _state.value = EqualizerUiState(
                supported = false,
                enabled = settings.equalizerEnabled.value,
                bassBoost = settings.bassBoost.value,
                virtualizer = settings.virtualizer.value,
                error = failure
            )
            return
        }

        // Bass and virtualizer are optional per device; a failure in either must not
        // disable the core equalizer.
        bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        bass?.apply {
            enabled = settings.equalizerEnabled.value
            setStrength((settings.bassBoost.value * 10).toShort())
        }
        virtualizer?.apply {
            enabled = settings.equalizerEnabled.value
            setStrength((settings.virtualizer.value * 10).toShort())
        }
        refresh()
    }

    fun setEnabled(value: Boolean) {
        settings.setEqualizerEnabled(value)
        equalizer?.enabled = value
        bass?.enabled = value
        virtualizer?.enabled = value
        refresh()
    }

    /** -1 is the custom curve; 0 and above are device preset indices. */
    fun setPreset(index: Int) {
        settings.setEqualizerPreset(index)
        val eq = equalizer
        if (eq != null && index in 0 until eq.numberOfPresets.toInt()) {
            runCatching { eq.usePreset(index.toShort()) }
            persistBands(eq)
        }
        refresh()
    }

    fun setBand(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val band = _state.value.bands.getOrNull(index) ?: return
        val clipped = levelMb.coerceIn(band.minMb, band.maxMb)
        runCatching { eq.setBandLevel(index.toShort(), clipped.toShort()) }
        settings.setEqualizerPreset(-1)
        persistBands(eq)
        refresh()
    }

    fun setBassBoost(percent: Int) {
        val value = percent.coerceIn(0, 100)
        settings.setBassBoost(value)
        bass?.setStrength((value * 10).toShort())
        bass?.enabled = settings.equalizerEnabled.value
        refresh()
    }

    fun setVirtualizer(percent: Int) {
        val value = percent.coerceIn(0, 100)
        settings.setVirtualizer(value)
        virtualizer?.setStrength((value * 10).toShort())
        virtualizer?.enabled = settings.equalizerEnabled.value
        refresh()
    }

    private fun refresh() {
        val eq = equalizer
        if (eq == null) {
            _state.value = _state.value.copy(attached = false, bands = emptyList())
            return
        }
        val range = eq.bandLevelRange
        val bands = (0 until eq.numberOfBands.toInt()).map { index ->
            val band = index.toShort()
            EqualizerBand(
                centerHz = eq.getCenterFreq(band) / 1000,
                levelMb = eq.getBandLevel(band).toInt(),
                minMb = range[0].toInt(),
                maxMb = range[1].toInt()
            )
        }
        val presets = buildList {
            add("Custom")
            for (index in 0 until eq.numberOfPresets.toInt()) {
                add(eq.getPresetName(index.toShort()))
            }
        }
        _state.value = EqualizerUiState(
            supported = true,
            attached = true,
            enabled = settings.equalizerEnabled.value,
            preset = settings.equalizerPreset.value,
            presets = presets,
            bands = bands,
            bassBoost = settings.bassBoost.value,
            virtualizer = settings.virtualizer.value
        )
    }

    private fun persistBands(eq: Equalizer) {
        val levels = (0 until eq.numberOfBands.toInt()).joinToString(",") { index ->
            eq.getBandLevel(index.toShort()).toString()
        }
        settings.setEqualizerBands(levels)
    }

    private fun savedLevels(): List<Int> = settings.equalizerBands.value
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bass = null
        virtualizer = null
        sessionId = 0
    }

    fun release() = releaseEffects()
}
