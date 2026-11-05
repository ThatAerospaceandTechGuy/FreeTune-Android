package com.freetune.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freetune.app.playback.PlayerUiState

@Composable
fun QueueDialog(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueue: (String) -> Unit
) {
    var showSave by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val currentIndex = state.queue.indexOfFirst { it.id == state.current?.id }
    val hasUpcoming = currentIndex in 0 until state.queue.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue · ${state.queue.size}") },
        text = {
            Column {
                if (state.queue.isEmpty()) {
                    Text(
                        text = "Nothing queued yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasUpcoming) {
                                "${state.queue.size - currentIndex - 1} upcoming"
                            } else {
                                "End of queue"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasUpcoming) {
                            TextButton(onClick = onClearUpcoming) {
                                Text("Clear upcoming")
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(
                            items = state.queue,
                            key = { index, track -> "$index-${track.id}" }
                        ) { index, track ->
                            val playing = track.id == state.current?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJump(index)
                                        onDismiss()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (playing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (playing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (playing) "Now playing" else track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveUp(index) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Move up",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveDown(index) },
                                    enabled = index < state.queue.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Move down",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove from queue",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.queue.isNotEmpty()) {
                    TextButton(onClick = { showSave = true }) {
                        Text("Save queue")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save queue as playlist") },
            text = {
                Column {
                    Text(
                        text = "${state.queue.size} songs will be saved in queue order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Playlist name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSaveQueue(name)
                        name = ""
                        showSave = false
                        onDismiss()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SleepTimerDialog(
    active: Boolean,
    atEnd: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    text = when {
                        atEnd -> "Playback stops when this song ends."
                        active -> "The timer is running. Pick a new length or turn it off."
                        else -> "Playback pauses automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(15, 30, 45, 60).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(minutes) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEndOfTrack() }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "End of this song",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (active) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCancel() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Turn the timer off",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
