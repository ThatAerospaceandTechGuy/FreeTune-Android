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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.EmptyHint
import com.freetune.app.ui.components.ErrorCard
import com.freetune.app.ui.components.TrackRow
import com.freetune.app.ui.components.downloadOrToast

@Composable
fun ArtistScreen(
    vm: MainViewModel,
    artist: String,
    onBack: () -> Unit
) {
    val state by vm.artist.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(artist) { vm.loadArtist(artist) }

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
            Text(
                text = "Artist",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(48.dp))
        }

        when {
            state.loading && state.tracks.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.error != null && state.tracks.isEmpty() -> Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ErrorCard(message = state.error!!) { vm.loadArtist(artist) }
            }

            state.tracks.isEmpty() -> EmptyHint(
                "No songs found for $artist",
                Modifier.fillMaxSize()
            )

            else -> {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = "${state.tracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val tracks = state.tracks
                                vm.play(tracks.first(), tracks)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                                val shuffled = state.tracks.shuffled()
                                vm.play(shuffled.first(), shuffled)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                            onClick = { state.tracks.firstOrNull()?.let(vm::startRadio) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Radio")
                        }
                    }
                }

                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.tracks, key = { it.id }) { track ->
                        val downloaded = downloadedIds.contains(track.id)
                        TrackRow(
                            track = track,
                            liked = likedIds.contains(track.id),
                            downloaded = downloaded,
                            onPlay = { vm.play(track, state.tracks) },
                            onStartRadio = { vm.startRadio(track) },
                            onPlayNext = { vm.playNext(track) },
                            onAddToQueue = { vm.addToQueue(track) },
                            onToggleLike = { vm.toggleLike(track) },
                            onDownload = {
                                downloadOrToast(context, downloaded) { vm.download(track) }
                            },
                            onAddToPlaylist = { vm.showPlaylistPicker(track) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}
