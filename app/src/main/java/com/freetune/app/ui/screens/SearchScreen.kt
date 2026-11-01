package com.freetune.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freetune.app.ui.MainViewModel
import com.freetune.app.ui.components.EmptyHint
import com.freetune.app.ui.components.ErrorCard
import com.freetune.app.ui.components.LoadingBox
import com.freetune.app.ui.components.TrackRow
import com.freetune.app.ui.components.downloadOrToast

@Composable
fun SearchScreen(vm: MainViewModel) {
    val state by vm.search.collectAsStateWithLifecycle()
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Songs, artists, albums…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        vm.submitSearch()
                        focusManager.clearFocus()
                    }
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    vm.submitSearch()
                    focusManager.clearFocus()
                },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search")
            }
        }

        when {
            state.loading -> LoadingBox()

            state.error != null -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ErrorCard(message = state.error!!) { vm.submitSearch() }
            }

            !state.hasSearched -> SearchIdle()

            state.results.isEmpty() -> EmptyHint(
                text = "No results for \"${state.query}\"",
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = "${state.results.size} results",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.results, key = { it.id }) { track ->
                    val downloaded = downloadedIds.contains(track.id)
                    TrackRow(
                        track = track,
                        liked = likedIds.contains(track.id),
                        downloaded = downloaded,
                        onPlay = { vm.play(track, state.results) },
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
private fun SearchIdle() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Find your next favorite",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Results appear automatically as you type. You can also press search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
