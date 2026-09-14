package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.MAMediaItem
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.components.Artwork
import com.versarepair.meloremote.ui.components.MediaActionsButton
import com.versarepair.meloremote.ui.components.MediaRow
import kotlinx.coroutines.delay

private enum class LibraryMode { PLAYLISTS, SEARCH }

@Composable
fun LibraryScreen(
    state: MeloUiState,
    viewModel: MeloRemoteViewModel,
    onSelectPlaylist: (MAMediaItem) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(LibraryMode.PLAYLISTS) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = mode == LibraryMode.PLAYLISTS,
                onClick = {
                    mode = LibraryMode.PLAYLISTS
                    query = ""
                },
                label = { Text("Playlists") },
            )
            FilterChip(
                selected = mode == LibraryMode.SEARCH,
                onClick = { mode = LibraryMode.SEARCH },
                label = { Text("Search") },
            )
        }
        if (mode == LibraryMode.SEARCH) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Artists, albums, tracks, playlists") },
                singleLine = true,
            )
            LaunchedEffect(query) {
                delay(350)
                viewModel.search(query)
            }
            SearchResults(state, query, viewModel)
        } else {
            PlaylistGrid(state, viewModel, onSelectPlaylist)
        }
    }
}

@Composable
private fun PlaylistGrid(
    state: MeloUiState,
    viewModel: MeloRemoteViewModel,
    onSelectPlaylist: (MAMediaItem) -> Unit,
) {
    if (state.playlists.isEmpty()) {
        EmptyMessage("No Playlists", "Playlists from Music Assistant will appear here.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(state.playlists, key = { it.id }) { playlist ->
            Column(Modifier.clickable { onSelectPlaylist(playlist) }) {
                Artwork(
                    url = viewModel.imageUrl(playlist, 512),
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (playlist.favorite == true) "Favorite playlist" else "Playlist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MediaActionsButton(playlist, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(state: MeloUiState, query: String, viewModel: MeloRemoteViewModel) {
    if (query.trim().length < 2) {
        EmptyMessage("Search your library", "Enter at least two characters.")
        return
    }
    val sections = listOf(
        "Tracks" to state.searchResult.tracks.orEmpty(),
        "Albums" to state.searchResult.albums.orEmpty(),
        "Artists" to state.searchResult.artists.orEmpty(),
        "Playlists" to state.searchResult.playlists.orEmpty(),
        "Radio" to state.searchResult.radio.orEmpty(),
        "Podcasts" to state.searchResult.podcasts.orEmpty(),
        "Audiobooks" to state.searchResult.audiobooks.orEmpty(),
    ).filter { it.second.isNotEmpty() }
    if (sections.isEmpty()) {
        EmptyMessage("No Results", "No matching library items were returned.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        sections.forEach { (title, entries) ->
            item(key = "heading-$title") {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { "$title-${it.id}" }) { item ->
                MediaRow(
                    item = item,
                    imageUrl = viewModel.imageUrl(item, 160),
                    onClick = { viewModel.play(item) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            MediaActionsButton(item, viewModel)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun EmptyMessage(title: String, description: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
