package com.freetune.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freetune.app.data.local.LocalAudioScanner
import com.freetune.app.data.local.LocalSongsState
import com.freetune.app.data.model.Track

/**
 * Lets the user pick audio that already exists on the device (Music folder, Download
 * folder, other apps' folders) and add it to a playlist.
 */
@Composable
fun LocalSongsDialog(
    state: LocalSongsState,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: (List<Track>) -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalAudioScanner.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
    }
    LaunchedEffect(granted) {
        if (granted) onLoad()
    }

    var folderPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Track>() }

    val visible = remember(state.folders, folderPath) {
        state.folders
            .filter { folderPath == null || it.path == folderPath }
            .flatMap { folder -> folder.songs.map { folder.label to it } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add from this device", modifier = Modifier.weight(1f))
                if (granted && !state.loading) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan folders"
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !granted -> {
                        Text(
                            "Songs already on this phone — in Music, Download, or another " +
                                "app's folder — can be added straight to this playlist.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "FreeTune only reads the audio files you pick. Nothing is uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { permissionLauncher.launch(LocalAudioScanner.permission()) }) {
                            Text("Allow access")
                        }
                    }

                    state.loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Scanning your device…")
                    }

                    state.error != null -> {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRefresh) { Text("Try again") }
                    }

                    state.folders.isEmpty() -> Text(
                        "No audio files found in Music, Downloads or other app folders yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> {
                        Text(
                            "${state.total} songs in ${state.folders.size} folders",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = folderPath == null,
                                    onClick = { folderPath = null },
                                    label = { Text("All") }
                                )
                            }
                            items(state.folders, key = { it.path }) { folder ->
                                FilterChip(
                                    selected = folderPath == folder.path,
                                    onClick = {
                                        folderPath =
                                            if (folderPath == folder.path) null else folder.path
                                    },
                                    label = {
                                        Text(
                                            "${folder.label} (${folder.songs.size})",
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(visible, key = { it.second.id }) { entry ->
                                val folderLabel = entry.first
                                val track = entry.second
                                val checked = selected.containsKey(track.id)
                                fun toggle() {
                                    if (checked) selected.remove(track.id)
                                    else selected[track.id] = track
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggle() }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { toggle() }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist} · " +
                                                "${formatDuration(track.durationMs)} · " +
                                                folderLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (selected.isNotEmpty()) {
                            Text(
                                "${selected.size} selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(selected.values.toList()) },
                enabled = selected.isNotEmpty()
            ) {
                Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
