package com.freetune.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.data.model.Playlist
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.EmptyHint
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
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                    text = "${tracks.size} songs · folder: ${playlist?.name ?: ""}/",
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
                onClick = { playlist?.let { vm.playPlaylist(it, tracks) } },
                enabled = tracks.isNotEmpty(),
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
                    playlist?.let { vm.playPlaylist(it, tracks, shuffle = true) }
                },
                enabled = tracks.isNotEmpty(),
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
                enabled = tracks.isNotEmpty(),
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

        if (tracks.isEmpty()) {
            EmptyHint(
                "This playlist is empty.\nAdd songs from Home or Search using the ⋮ menu.",
                Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val downloaded = downloadedIds.contains(track.id)
                    TrackRow(
                        track = track,
                        liked = likedIds.contains(track.id),
                        downloaded = downloaded,
                        onPlay = { vm.play(track, tracks) },
                        onToggleLike = { vm.toggleLike(track) },
                        onDownload = {
                            downloadOrToast(context, downloaded) {
                                vm.download(track, folder = playlist?.name)
                            }
                        },
                        onAddToPlaylist = { vm.showPlaylistPicker(track) },
                        onRemoveFromPlaylist = {
                            vm.removeFromPlaylist(playlistId, track)
                        }
                    )
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
}
