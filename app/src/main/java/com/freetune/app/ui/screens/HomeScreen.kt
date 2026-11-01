package com.freetune.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.data.model.Track
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.AsyncArtwork
import com.freetune.app.ui.components.EmptyHint
import com.freetune.app.ui.components.ErrorCard
import com.freetune.app.ui.components.SectionHeader
import com.freetune.app.ui.components.TrackRow
import com.freetune.app.ui.components.downloadOrToast

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenSearch: () -> Unit
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val quickPicks = remember {
        listOf("Top songs 2026", "Lo-fi beats", "Chill hits", "Workout music", "Trending music")
    }

    LaunchedEffect(Unit) { vm.ensureDiscoverLoaded() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "FreeTune",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSearch)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Search songs, artists, albums",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "YOUR DAILY MIX",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = discover.query,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (discover.results.isNotEmpty()) {
                            "${discover.results.size} tracks ready · ${discover.results.first().artist} and more"
                        } else {
                            "Pick a mood below and start listening"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = {
                                if (discover.results.isNotEmpty()) {
                                    val shuffled = discover.results.shuffled()
                                    vm.play(shuffled.first(), shuffled)
                                } else {
                                    vm.loadDiscover(discover.query)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (discover.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play mix"
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (discover.loading) "Loading mix…" else "Shuffle play",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
            ) {
                items(quickPicks) { pick ->
                    FilterChip(
                        selected = discover.query == pick,
                        onClick = { vm.loadDiscover(pick) },
                        label = { Text(pick) }
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader("Jump back in", modifier = Modifier.padding(horizontal = 20.dp)) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    items(recent.take(8), key = { "recent-${it.id}" }) { track ->
                        TrackCard(track = track, onClick = { vm.play(track, recent) })
                    }
                }
            }
        }

        if (liked.isNotEmpty()) {
            item { SectionHeader("Liked songs", modifier = Modifier.padding(horizontal = 20.dp)) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    items(liked.take(8), key = { "liked-${it.id}" }) { track ->
                        TrackCard(track = track, onClick = { vm.play(track, liked) })
                    }
                }
            }
        }

        item {
            SectionHeader(
                "Discover · ${discover.query}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        when {
            discover.loading && discover.results.isEmpty() -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            discover.error != null -> item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ErrorCard(message = discover.error!!) {
                        vm.loadDiscover(discover.query)
                    }
                }
            }

            discover.results.isEmpty() -> item {
                EmptyHint("No results right now. Try a different mood.")
            }

            else -> items(
                items = discover.results.take(20),
                key = { "discover-${it.id}" }
            ) { track ->
                val downloaded = downloadedIds.contains(track.id)
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    TrackRow(
                        track = track,
                        liked = likedIds.contains(track.id),
                        downloaded = downloaded,
                        onPlay = { vm.play(track, discover.results) },
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

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncArtwork(
            url = track.thumbnailUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp),
            corner = 16.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
