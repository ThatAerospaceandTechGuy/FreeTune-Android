package com.freetune.app.ui.screens

import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.BuildConfig
import com.freetune.app.data.model.AudioQuality
import com.freetune.app.data.repo.MusicRepository
import com.freetune.app.playback.OverlayService
import com.freetune.app.ui.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val quality by vm.quality.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val floatingControls by vm.floatingControls.collectAsStateWithLifecycle()
    val autoplay by vm.autoplay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEraseDialog by rememberSaveable { mutableStateOf(false) }
    var pendingOverlay by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingOverlay) {
                if (Settings.canDrawOverlays(context)) {
                    pendingOverlay = false
                    vm.setFloatingControls(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        SettingsCard(title = "Audio quality") {
            AudioQuality.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setQuality(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = quality == option,
                        onClick = { vm.setQuality(option) }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SettingsCard(title = "Downloads") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Queue downloads until Wi-Fi is available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnly)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Saved to ${MusicRepository.downloadDir(context).absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(title = "Playback") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Autoplay", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Keep playing related songs when the queue ends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoplay, onCheckedChange = vm::setAutoplay)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating controls", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A playback bar over other apps with previous, play/pause and next. Requires the display over other apps permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = floatingControls,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (Settings.canDrawOverlays(context)) {
                                vm.setFloatingControls(true)
                            } else {
                                pendingOverlay = true
                                OverlayService.openOverlaySettings(context)
                                Toast.makeText(
                                    context,
                                    "Allow display over other apps, then return",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            pendingOverlay = false
                            vm.setFloatingControls(false)
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Playback controls also appear in the notification shade and on your lock screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(title = "Privacy") {
            Text(
                text = "No accounts. No analytics. No ads. Your library, history and downloads stay on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::clearRecent) {
                Text("Clear recent history")
            }
            TextButton(
                onClick = { showEraseDialog = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Erase all app data")
            }
        }

        SettingsCard(title = "About") {
            Text(
                text = "FreeTune ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Sideloaded, open-source build. GPL-3.0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Audio is streamed and downloaded from YouTube. Downloads are for personal use only. Respect copyright and YouTube's Terms of Service.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Erase everything?") },
            text = {
                Text("This deletes your likes, history, downloads and settings. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAll()
                        showEraseDialog = false
                        Toast.makeText(context, "All data erased", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
