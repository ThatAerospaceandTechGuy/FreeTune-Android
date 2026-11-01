package com.freetune.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.data.download.DownloadState
import com.freetune.app.data.download.DownloadStatus
import com.freetune.app.data.model.Playlist
import com.freetune.app.data.model.Track
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.CreatePlaylistDialog
import com.freetune.app.ui.components.EmptyHint
import com.freetune.app.ui.components.SectionHeader
import com.freetune.app.ui.components.TrackRow
import com.freetune.app.ui.components.downloadOrToast

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpenPlaylist: (Long) -> Unit
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val statuses by vm.downloadStatuses.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(0) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (tab == 0) {
                FilledTonalButton(onClick = { showCreate = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
            }
        }

        TabRow(selectedTabIndex = tab) {
            listOf("Playlists", "Liked", "Downloads", "Recent").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, maxLines = 1) }
                )
            }
        }

        when (tab) {
            0 -> PlaylistsTab(
                playlists = playlists,
                onOpen = onOpenPlaylist,
                onPlay = { playlist -> vm.playPlaylist(playlist) },
                onShuffle = { playlist -> vm.playPlaylist(playlist, shuffle = true) },
                onDownload = vm::requestPlaylistDownload,
                onCreate = { showCreate = true },
                onDelete = { pendingDelete = it }
            )

            1 -> {
                if (liked.isEmpty()) {
                    EmptyHint("Songs you like will appear here", Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(liked, key = { it.id }) { track ->
                            val downloaded = downloadedIds.contains(track.id)
                            TrackRow(
                                track = track,
                                liked = true,
                                downloaded = downloaded,
                                onPlay = { vm.play(track, liked) },
                                onToggleLike = { vm.toggleLike(track) },
                                onDownload = {
                                    downloadOrToast(context, downloaded) { vm.download(track) }
                                },
                                onAddToPlaylist = { vm.showPlaylistPicker(track) }
                            )
                        }
                    }
                }
            }

            2 -> DownloadsTab(
                statuses = statuses,
                downloads = downloads,
                likedIds = likedIds,
                downloadedIds = downloadedIds,
                vm = vm,
                context = context
            )

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${recent.size} played",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = vm::clearRecent) { Text("Clear") }
                    }
                    if (recent.isEmpty()) {
                        EmptyHint("Your listening history is empty", Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(recent, key = { it.id }) { track ->
                                val downloaded = downloadedIds.contains(track.id)
                                TrackRow(
                                    track = track,
                                    liked = likedIds.contains(track.id),
                                    downloaded = downloaded,
                                    onPlay = { vm.play(track, recent) },
                                    onToggleLike = { vm.toggleLike(track) },
                                    onDownload = {
                                        downloadOrToast(context, downloaded) { vm.download(track) }
                                    },
                                    onAddToPlaylist = { vm.showPlaylistPicker(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                vm.createPlaylist(name)
                showCreate = false
            }
        )
    }

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "\"${playlist.name}\" will be removed. Downloaded files stay on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist(playlist)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    onOpen: (Long) -> Unit,
    onPlay: (Playlist) -> Unit,
    onShuffle: (Playlist) -> Unit,
    onDownload: (Playlist) -> Unit,
    onCreate: () -> Unit,
    onDelete: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
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
            Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Group songs and download each playlist into its own folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create playlist")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onOpen = { onOpen(playlist.id) },
                onPlay = { onPlay(playlist) },
                onShuffle = { onShuffle(playlist) },
                onDownload = { onDownload(playlist) },
                onDelete = { onDelete(playlist) }
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} songs · downloads to ${playlist.name}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play playlist",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Download playlist",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist options",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            onClick = {
                                menuOpen = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Shuffle") },
                            onClick = {
                                menuOpen = false
                                onShuffle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Download playlist…") },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open") },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsTab(
    statuses: Map<String, DownloadStatus>,
    downloads: List<Track>,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    vm: MainViewModel,
    context: android.content.Context
) {
    val active = statuses.values
        .filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
        .sortedBy { it.track.title }
    val failed = statuses.values
        .filter { it.state == DownloadState.FAILED }
        .sortedBy { it.track.title }

    if (active.isEmpty() && failed.isEmpty() && downloads.isEmpty()) {
        EmptyHint("Downloads are saved on this device", Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (active.isNotEmpty()) {
            item { SectionHeader("In progress") }
            items(active, key = { "active-${it.track.id}" }) { status ->
                ActiveDownloadRow(status)
            }
        }

        if (failed.isNotEmpty()) {
            item { SectionHeader("Failed") }
            items(failed, key = { "failed-${it.track.id}" }) { status ->
                FailedDownloadRow(
                    status = status,
                    onRetry = { vm.retryDownload(status) },
                    onDismiss = { vm.dismissDownload(status.track.id) }
                )
            }
        }

        if (downloads.isNotEmpty()) {
            item { SectionHeader("Downloaded · ${downloads.size}") }
            items(downloads, key = { "downloaded-${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    liked = likedIds.contains(track.id),
                    downloaded = downloadedIds.contains(track.id),
                    onPlay = { vm.play(track, downloads) },
                    onToggleLike = { vm.toggleLike(track) },
                    onDownload = {},
                    onRemoveDownload = {
                        vm.removeDownload(track)
                        Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                    },
                    onAddToPlaylist = { vm.showPlaylistPicker(track) }
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(status: DownloadStatus) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = status.track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (status.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (status.progress > 0) "${status.progress}%" else "Preparing…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FailedDownloadRow(
    status: DownloadStatus,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = status.message ?: "Download failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
