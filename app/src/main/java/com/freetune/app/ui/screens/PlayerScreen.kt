package com.freetune.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.playback.RepeatMode
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.AsyncArtwork
import com.freetune.app.ui.components.EmptyHint
import com.freetune.app.ui.components.LyricsView
import com.freetune.app.ui.components.QueueDialog
import com.freetune.app.ui.components.SleepTimerDialog
import com.freetune.app.ui.components.formatDuration
import com.freetune.app.ui.components.shareTrack

@Composable
fun PlayerScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit
) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val autoplay by vm.autoplay.collectAsStateWithLifecycle()
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val lyricsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.attachLyrics(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(48.dp))
        }

        val track = state.current
        if (track == null) {
            EmptyHint("Nothing playing yet", Modifier.fillMaxSize())
            return@Column
        }

        val liked = likedIds.contains(track.id)
        val downloaded = downloadedIds.contains(track.id)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            AsyncArtwork(
                url = track.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                corner = 24.dp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenArtist(track.artist) }
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleLike(track) }) {
                    Icon(
                        imageVector = if (liked) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = if (liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.showPlaylistPicker(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { if (!downloaded) vm.download(track) },
                    enabled = !downloaded
                ) {
                    Icon(
                        imageVector = if (downloaded) Icons.Rounded.DownloadDone
                        else Icons.Rounded.Download,
                        contentDescription = if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { shareTrack(context, track) }) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share song",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        state.autoplayLoading -> "Finding more songs…"
                        downloaded -> "Downloaded"
                        else -> "Streaming"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            var dragValue by remember(track.id) { mutableFloatStateOf(state.positionMs.toFloat()) }
            var dragging by remember(track.id) { mutableStateOf(false) }
            LaunchedEffect(state.positionMs) {
                if (!dragging) dragValue = state.positionMs.toFloat()
            }
            val duration = state.durationMs.coerceAtLeast(1L)

            Slider(
                value = dragValue.coerceIn(0f, duration.toFloat()),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    vm.seekTo(dragValue.toLong())
                },
                valueRange = 0f..duration.toFloat()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(dragValue.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }
                FilledIconButton(
                    onClick = vm::togglePlayPause,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause
                            else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = vm::cycleRepeatMode) {
                    Icon(
                        imageVector = if (state.repeatMode == RepeatMode.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode == RepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (state.repeatMode) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat one"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { showSleep = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (state.sleepRemainingMs > 0L || state.sleepAtEnd) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.sleepAtEnd -> "After song"
                            state.sleepRemainingMs > 0L ->
                                formatDuration(state.sleepRemainingMs)
                            else -> "Sleep"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { showQueue = true }) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Queue", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lyrics", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (lyricsState.manual) {
                    TextButton(onClick = vm::removeLyrics) { Text("Remove") }
                }
                TextButton(onClick = { lyricsPicker.launch(arrayOf("*/*")) }) {
                    Text(if (lyricsState.lyrics == null) "Add .lrc" else "Change")
                }
            }
            val currentLyrics = lyricsState.lyrics
            when {
                lyricsState.loading -> Text(
                    text = "Looking for lyrics…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                currentLyrics != null -> Column {
                    if (currentLyrics.instrumental) {
                        Text(
                            text = "Instrumental track — no lyrics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LyricsView(
                            lyrics = currentLyrics,
                            positionMs = state.positionMs
                        )
                    }
                    if (currentLyrics.source != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Lyrics · ${currentLyrics.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> Text(
                    text = "No lyrics found for this song. Add a .lrc file to use your " +
                        "own instead of the online lookup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Autoplay", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.autoplayLoading) {
                            "Finding more songs like this…"
                        } else {
                            "Keep playing related songs when the queue ends"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoplay,
                    onCheckedChange = vm::setAutoplay
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQueue) {
        QueueDialog(
            state = state,
            onDismiss = { showQueue = false },
            onJump = vm::jumpToQueue,
            onRemove = vm::removeFromQueue,
            onMoveUp = { index -> vm.moveQueueItem(index, index - 1) },
            onMoveDown = { index -> vm.moveQueueItem(index, index + 1) },
            onClearUpcoming = vm::clearUpcoming,
            onSaveQueue = vm::saveQueueAsPlaylist
        )
    }
    if (showSleep) {
        SleepTimerDialog(
            active = state.sleepRemainingMs > 0L || state.sleepAtEnd,
            atEnd = state.sleepAtEnd,
            onDismiss = { showSleep = false },
            onPick = { minutes ->
                vm.setSleepTimer(minutes)
                showSleep = false
            },
            onEndOfTrack = {
                vm.setSleepAtEndOfTrack()
                showSleep = false
            },
            onCancel = {
                vm.cancelSleepTimer()
                showSleep = false
            }
        )
    }
}
