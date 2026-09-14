package com.versarepair.meloremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.versarepair.meloremote.data.model.MAMediaItem
import com.versarepair.meloremote.ui.components.MediaActionsButton
import com.versarepair.meloremote.ui.screens.LibraryScreen
import com.versarepair.meloremote.ui.screens.LoginScreen
import com.versarepair.meloremote.ui.screens.NowPlayingScreen
import com.versarepair.meloremote.ui.screens.PlayersScreen
import com.versarepair.meloremote.ui.screens.PlaylistDetailScreen
import com.versarepair.meloremote.ui.screens.QueueScreen
import com.versarepair.meloremote.ui.screens.SettingsScreen

private data class TabDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    TabDestination("Listen", Icons.Rounded.PlayCircle),
    TabDestination("Library", Icons.Rounded.LibraryMusic),
    TabDestination("Queue", Icons.AutoMirrored.Rounded.QueueMusic),
    TabDestination("Speakers", Icons.Rounded.SpeakerGroup),
    TabDestination("Settings", Icons.Rounded.Settings),
)

@Composable
fun MeloRemoteRoot(viewModel: MeloRemoteViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        if (state.isAuthenticated) {
            MainApp(state, viewModel)
        } else {
            LoginScreen(state, viewModel)
        }
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Music Assistant") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<MAMediaItem?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val title = selectedPlaylist?.title ?: when (selectedTab) {
        0 -> "Now Playing"
        else -> destinations[selectedTab].label
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (selectedPlaylist != null) {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    val playlist = selectedPlaylist
                    if (playlist != null) {
                        MediaActionsButton(playlist, viewModel)
                    } else {
                        when (selectedTab) {
                            0 -> {
                                state.activeQueue?.currentItem?.mediaItem?.let {
                                    MediaActionsButton(it, viewModel, includePlayNow = false)
                                }
                                PlayerSelector(state, viewModel)
                            }
                            2 -> IconButton(
                                onClick = { showClearConfirmation = true },
                                enabled = state.queueItems.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear queue")
                            }
                        }
                        if (selectedTab in 0..3) {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedPlaylist = null
                            selectedTab = index
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        MainContent(
            state = state,
            viewModel = viewModel,
            selectedTab = selectedTab,
            selectedPlaylist = selectedPlaylist,
            onSelectPlaylist = { selectedPlaylist = it },
            padding = padding,
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear queue?") },
            text = { Text("This removes every item from the selected speaker’s queue.") },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    viewModel.clearQueue()
                }) { Text("Clear") }
            },
        )
    }
}

@Composable
private fun MainContent(
    state: MeloUiState,
    viewModel: MeloRemoteViewModel,
    selectedTab: Int,
    selectedPlaylist: MAMediaItem?,
    onSelectPlaylist: (MAMediaItem) -> Unit,
    padding: PaddingValues,
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        if (selectedPlaylist != null) {
            PlaylistDetailScreen(selectedPlaylist, viewModel)
        } else {
            when (selectedTab) {
                0 -> NowPlayingScreen(state, viewModel)
                1 -> LibraryScreen(state, viewModel, onSelectPlaylist)
                2 -> QueueScreen(state, viewModel)
                3 -> PlayersScreen(state, viewModel)
                else -> SettingsScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun PlayerSelector(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Cast, contentDescription = "Choose speaker")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.visiblePlayers.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.title) },
                    leadingIcon = {
                        Icon(
                            if (state.activePlayer?.playerId == player.playerId) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.SpeakerGroup
                            },
                            contentDescription = null,
                        )
                    },
                    enabled = player.isAvailable,
                    onClick = {
                        expanded = false
                        viewModel.selectPlayer(player)
                    },
                )
            }
        }
    }
}
