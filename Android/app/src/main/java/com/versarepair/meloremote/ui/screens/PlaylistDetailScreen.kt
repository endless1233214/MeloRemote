package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.MAMediaItem
import com.versarepair.meloremote.data.model.QueuePlaybackOption
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.components.Artwork
import com.versarepair.meloremote.ui.components.MediaActionsButton
import com.versarepair.meloremote.ui.components.MediaRow

@Composable
fun PlaylistDetailScreen(playlist: MAMediaItem, viewModel: MeloRemoteViewModel) {
    var tracks by remember(playlist.id) { mutableStateOf<List<MAMediaItem>>(emptyList()) }
    var loading by remember(playlist.id) { mutableStateOf(true) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(playlist.id) {
        runCatching { viewModel.playlistTracks(playlist) }
            .onSuccess { tracks = it }
            .onFailure { error = it.message ?: "Unable to load this playlist." }
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    url = viewModel.imageUrl(playlist, 1024),
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 280.dp).aspectRatio(1f),
                    cornerRadius = 12,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    playlist.title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { viewModel.play(playlist) }, modifier = Modifier.weight(1f)) {
                        Text("Play")
                    }
                    MediaActionsButton(playlist, viewModel)
                }
                Spacer(Modifier.height(22.dp))
                Text("Tracks", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
            }
        }
        when {
            loading -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
            error != null -> item { EmptyMessage("Unable to Load", requireNotNull(error)) }
            else -> items(tracks, key = { it.id }) { track ->
                MediaRow(
                    item = track,
                    imageUrl = null,
                    showArtwork = false,
                    onClick = { viewModel.play(track) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.play(track, QueuePlaybackOption.ADD) }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Add ${track.title} to queue",
                                )
                            }
                            MediaActionsButton(track, viewModel)
                        }
                    },
                )
            }
        }
    }
}
