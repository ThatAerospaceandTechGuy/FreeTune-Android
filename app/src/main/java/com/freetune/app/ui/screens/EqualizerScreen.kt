package com.freetune.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.ui.MainViewModel
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.equalizer.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = vm::setEqualizerEnabled,
                enabled = state.supported
            )
        }

        Text(
            text = "Effects apply to FreeTune playback only. Bluetooth and wired " +
                "headphones may expose different bands or ignore some effects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!state.supported) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.error
                        ?: "This device does not support audio effects for the player.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }

        if (!state.attached || state.bands.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Start a song, then return here to load this device's bands.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
        } else {
            Text("Presets", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.presets) { index, name ->
                    FilterChip(
                        selected = state.preset == index - 1,
                        onClick = { vm.setEqualizerPreset(index - 1) },
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text("Bands", style = MaterialTheme.typography.titleMedium)
            state.bands.forEachIndexed { index, band ->
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = formatHz(band.centerHz),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatDb(band.levelMb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = band.levelMb.toFloat(),
                        onValueChange = { vm.setEqualizerBand(index, it.roundToInt()) },
                        valueRange = band.minMb.toFloat()..band.maxMb.toFloat()
                    )
                }
            }
        }

        Text("Bass boost", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.bassBoost.toFloat(),
            onValueChange = { vm.setBassBoost(it.roundToInt()) },
            valueRange = 0f..100f
        )
        Text(
            text = "${state.bassBoost}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Virtualizer", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Widens stereo output; most noticeable with headphones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = state.virtualizer.toFloat(),
            onValueChange = { vm.setVirtualizer(it.roundToInt()) },
            valueRange = 0f..100f
        )
        Text(
            text = "${state.virtualizer}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
    }
}

private fun formatHz(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"

private fun formatDb(levelMb: Int): String = "%+.1f dB".format(levelMb / 100f)
