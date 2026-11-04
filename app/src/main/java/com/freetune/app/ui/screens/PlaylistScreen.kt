package com.freetune.app.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.data.model.Track
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.LocalSongsDialog
import com.freetune.app.ui.components.TrackRow
import com.freetune.app.ui.components.downloadOrToast

@Composable
fun PlaylistScreen(
    vm: MainViewModel,
    playlistId: Long,
    onBack: () -> Unit
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val trackFlow = remember(playlistId) { vm.playlistTracks(playlistId) }
    val tracks by trackFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val localSongs by vm.localSongs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showLocalSongs by rememberSaveable { mutableStateOf(false) }

    // Reordering: `ordered` is the list the user sees while dragging. It is committed
    // to the database on drop and resynced from the database afterwards.
    var ordered by remember(playlistId) { mutableStateOf(tracks) }
    var draggingId by remember(playlistId) { mutableStateOf<String?>(null) }
    var dragOffset by remember(playlistId) { mutableFloatStateOf(0f) }
    var pendingReorder by remember(playlistId) { mutableStateOf(false) }

    val rowHeight = 76.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    LaunchedEffect(tracks) {
        if (draggingId != null) return@LaunchedEffect
        if (pendingReorder) {
            // Ignore the pre-drop order while the new positions are being saved.
            if (tracks.map { it.id } == ordered.map { it.id }) {
                pendingReorder = false
                ordered = tracks
            }
            return@LaunchedEffect
        }
        ordered = tracks
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${ordered.size} songs · folder: ${playlist?.name ?: ""}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add songs from this device") },
                        onClick = {
                            menuOpen = false
                            showLocalSongs = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { playlist?.let { vm.playPlaylist(it, ordered) } },
                enabled = ordered.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Play all")
            }
            FilledTonalButton(
                onClick = {
                    playlist?.let { vm.playPlaylist(it, ordered, shuffle = true) }
                },
                enabled = ordered.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Shuffle")
            }
            FilledTonalButton(
                onClick = { playlist?.let { vm.requestPlaylistDownload(it) } },
                enabled = ordered.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Download")
            }
        }

        if (ordered.size > 1) {
            Text(
                text = "Long-press a song to drag it into a new position",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }

        if (ordered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("This playlist is empty", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add songs from Home or Search, or import audio that is already " +
                        "on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { showLocalSongs = true }) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add from device")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                itemsIndexed(ordered, key = { _, track -> track.id }) { _, track ->
                    val dragging = draggingId == track.id
                    val downloaded = downloadedIds.contains(track.id)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                            }
                            .pointerInput(playlistId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = track.id
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        var current = ordered.indexOfFirst { it.id == track.id }
                                        if (current < 0) return@detectDragGesturesAfterLongPress
                                        while (dragOffset > rowHeightPx / 2f &&
                                            current < ordered.lastIndex
                                        ) {
                                            ordered = ordered.swapped(current, current + 1)
                                            dragOffset -= rowHeightPx
                                            current++
                                        }
                                        while (dragOffset < -rowHeightPx / 2f && current > 0) {
                                            ordered = ordered.swapped(current, current - 1)
                                            dragOffset += rowHeightPx
                                            current--
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        if (ordered.map { it.id } != tracks.map { it.id }) {
                                            pendingReorder = true
                                            vm.reorderPlaylist(playlistId, ordered)
                                        }
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        TrackRow(
                            track = track,
                            liked = likedIds.contains(track.id),
                            downloaded = downloaded,
                            onPlay = { vm.play(track, ordered) },
                            onStartRadio = { vm.startRadio(track) },
                            onPlayNext = { vm.playNext(track) },
                            onAddToQueue = { vm.addToQueue(track) },
                            onToggleLike = { vm.toggleLike(track) },
                            onDownload = {
                                downloadOrToast(context, downloaded) {
                                    vm.download(track, folder = playlist?.name)
                                }
                            },
                            onAddToPlaylist = { vm.showPlaylistPicker(track) },
                            onRemoveFromPlaylist = {
                                vm.removeFromPlaylist(playlistId, track)
                            },
                            showDownload = track.contentUri == null,
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "\"${playlist?.name ?: "Playlist"}\" will be removed. Downloaded files stay on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlist?.let { vm.deletePlaylist(it) }
                        confirmDelete = false
                        onBack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (showLocalSongs) {
        LocalSongsDialog(
            state = localSongs,
            onDismiss = { showLocalSongs = false },
            onLoad = { vm.scanLocalSongs() },
            onRefresh = { vm.scanLocalSongs(force = true) },
            onAdd = { songs ->
                playlist?.let { vm.importLocalTracks(it, songs) }
                showLocalSongs = false
            }
        )
    }
}

private fun List<Track>.swapped(from: Int, to: Int): List<Track> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
