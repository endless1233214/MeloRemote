package com.versarepair.meloremote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.versarepair.meloremote.data.model.ConnectionState
import com.versarepair.meloremote.data.model.MAMediaItem
import com.versarepair.meloremote.data.model.QueuePlaybackOption
import com.versarepair.meloremote.data.model.formattedDuration
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.theme.MeloAccent
import com.versarepair.meloremote.ui.theme.MeloAmber
import com.versarepair.meloremote.ui.theme.MeloMint

@Composable
fun AppMark(size: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape((size * 0.18f).dp),
        color = Color(0xFF0B1822),
        shadowElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((size * 0.62f).dp)
                    .border((size * 0.065f).dp, Color(0xFF2BD5C7), CircleShape),
            )
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.42f).dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding((size * 0.14f).dp)
                    .size((size * 0.15f).dp)
                    .background(Color(0xFFFFC857), CircleShape),
            )
        }
    }
}

@Composable
fun ConnectionPill(state: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        ConnectionState.Connected -> "Connected" to MeloMint
        ConnectionState.Connecting -> "Connecting" to MeloAmber
        ConnectionState.Disconnected -> "Offline" to MaterialTheme.colorScheme.error
        is ConnectionState.Failed -> "Connection lost" to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MediaRow(
    item: MAMediaItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                cornerRadius = 6,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotEmpty()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        else item.duration?.let {
            Text(
                it.formattedDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MediaActionsButton(
    item: MAMediaItem,
    viewModel: MeloRemoteViewModel,
    includePlayback: Boolean = true,
    includePlayNow: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Actions for ${item.title}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includePlayback && item.canPlay) {
                if (includePlayNow) {
                    ActionItem("Play Now", Icons.Rounded.PlayArrow) {
                        expanded = false
                        viewModel.play(item)
                    }
                }
                ActionItem("Play After Current", Icons.Rounded.Album) {
                    expanded = false
                    viewModel.play(item, QueuePlaybackOption.PLAY)
                }
                ActionItem("Play Next", Icons.Rounded.SkipNext) {
                    expanded = false
                    viewModel.play(item, QueuePlaybackOption.NEXT)
                }
                ActionItem("Add to Queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                    expanded = false
                    viewModel.play(item, QueuePlaybackOption.ADD)
                }
            }
            if (includePlayback && item.canStartRadio) {
                ActionItem("Start Radio", Icons.Rounded.Radio) {
                    expanded = false
                    viewModel.startRadio(item)
                }
            }
            if (item.canChangeLibraryMembership) {
                ActionItem(
                    if (item.isInLibrary) "Remove from Library" else "Add to Library",
                    if (item.isInLibrary) Icons.Rounded.DeleteOutline else Icons.Rounded.LibraryAdd,
                ) {
                    expanded = false
                    viewModel.toggleLibraryMembership(item)
                }
            }
            if (item.canBeFavorited) {
                ActionItem(
                    if (item.favorite == true) "Unfavorite" else "Favorite",
                    if (item.favorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                ) {
                    expanded = false
                    viewModel.toggleFavorite(item)
                }
            }
            if (item.canBeAddedToPlaylist) {
                ActionItem("Add to Playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                    expanded = false
                    showAddToPlaylist = true
                }
            }
        }
    }
    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylist = false },
        )
    }
}

@Composable
private fun ActionItem(label: String, icon: ImageVector, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = action,
    )
}

@Composable
private fun AddToPlaylistDialog(
    item: MAMediaItem,
    viewModel: MeloRemoteViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val targets = remember(item, state.playlists) {
        viewModel.playlistTargets(item, state.playlists)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            if (targets.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, Modifier.size(40.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("No editable playlists can accept this item.")
                }
            } else {
                LazyColumn {
                    items(targets, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.addToPlaylist(item, playlist)
                                onDismiss()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = MeloAccent)
                            Text(playlist.title, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.AddCircle, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
