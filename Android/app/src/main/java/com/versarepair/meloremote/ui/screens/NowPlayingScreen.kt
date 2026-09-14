package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.RepeatMode
import com.versarepair.meloremote.data.model.formattedDuration
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.components.Artwork
import com.versarepair.meloremote.ui.components.ConnectionPill
import com.versarepair.meloremote.ui.theme.MeloAccent
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    val player = state.activePlayer
    val queue = state.activeQueue
    val isPlaying = queue?.isPlaying == true || player?.effectiveState == "playing"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(player?.title ?: "No speaker", fontWeight = FontWeight.SemiBold)
                Text(
                    if (player?.isAvailable == true) "Available" else "Unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionPill(state.connectionState)
        }

        Artwork(
            url = viewModel.artworkUrl(),
            contentDescription = "Artwork for ${state.nowPlayingTitle}",
            modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp).aspectRatio(1f),
            cornerRadius = 12,
        )

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.nowPlayingTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.nowPlayingSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ProgressControl(state, viewModel)

        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = viewModel::playPause,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                    tint = Color.White,
                )
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", Modifier.size(34.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (queue?.shuffleEnabled == true) MeloAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val repeatMode = RepeatMode.fromWire(queue?.repeatMode)
            IconButton(onClick = viewModel::cycleRepeatMode) {
                Icon(
                    if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat ${repeatMode.wireValue}",
                    tint = if (repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MeloAccent,
                )
            }
        }

        VolumeControl(state, viewModel)
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun ProgressControl(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableDoubleStateOf(0.0) }
    val duration = state.nowPlayingDuration.coerceAtLeast(1.0)
    LaunchedEffect(state.activeQueue?.isPlaying) {
        while (state.activeQueue?.isPlaying == true) {
            delay(1_000)
            tick++
        }
    }
    val live = state.activeQueue?.correctedElapsedTime()?.coerceIn(0.0, duration) ?: 0.0
    val shown = if (editing) draft else live
    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Slider(
            value = shown.toFloat(),
            onValueChange = {
                editing = true
                draft = it.toDouble()
            },
            onValueChangeFinished = {
                editing = false
                viewModel.seek(draft)
            },
            valueRange = 0f..duration.toFloat(),
            enabled = state.nowPlayingDuration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shown.formattedDuration(), style = MaterialTheme.typography.labelSmall)
            Text(state.nowPlayingDuration.formattedDuration(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VolumeControl(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    val player = state.activePlayer
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableDoubleStateOf(player?.effectiveVolume ?: 0.0) }
    LaunchedEffect(player?.effectiveVolume) {
        if (!editing) draft = player?.effectiveVolume ?: 0.0
    }
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::toggleMute) {
            Icon(
                if (player?.isMuted == true) Icons.AutoMirrored.Rounded.VolumeOff
                else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Mute",
            )
        }
        Slider(
            value = draft.toFloat(),
            onValueChange = {
                editing = true
                draft = it.toDouble()
            },
            onValueChangeFinished = {
                editing = false
                viewModel.setVolume(draft)
            },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
    }
}
