package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.MAPlayer
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.theme.MeloAccent
import com.versarepair.meloremote.ui.theme.MeloAmber
import com.versarepair.meloremote.ui.theme.MeloBlue
import com.versarepair.meloremote.ui.theme.MeloMint
import java.util.Locale

@Composable
fun PlayersScreen(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle("Output") }
        items(state.visiblePlayers, key = { it.playerId }) { player ->
            PlayerRow(player, state.activePlayer?.playerId == player.playerId) {
                if (player.isAvailable) viewModel.selectPlayer(player)
            }
        }
        state.activePlayer?.let { player ->
            item {
                SectionTitle("Selected Speaker")
                DetailRow("Provider", providerName(player.provider))
                DetailRow("Volume", "${player.effectiveVolume.toInt()}%")
                DetailRow("Playback", providerName(player.effectiveState))
            }
        }
    }
}

@Composable
private fun PlayerRow(player: MAPlayer, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = player.isAvailable, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val color = playerColor(player)
        Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.14f)) {
            Icon(
                playerIcon(player),
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(12.dp).size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(player.title, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = CircleShape,
                    color = if (player.isAvailable) MeloMint else MaterialTheme.colorScheme.outline,
                ) {}
                Text(
                    statusText(player),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MeloAccent)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        text = value,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun statusText(player: MAPlayer): String = when {
    !player.isAvailable -> "Unavailable"
    player.effectiveState == "playing" -> "Playing"
    player.effectiveState == "paused" -> "Paused"
    else -> "Ready"
}

private fun playerIcon(player: MAPlayer): ImageVector = when {
    player.provider?.contains("airplay") == true -> Icons.Rounded.Cast
    player.provider?.contains("hass") == true -> Icons.Rounded.Tv
    player.provider?.contains("snapcast") == true -> Icons.Rounded.SpeakerGroup
    else -> Icons.AutoMirrored.Rounded.VolumeUp
}

private fun playerColor(player: MAPlayer): Color = when {
    !player.isAvailable -> Color.Gray
    player.effectiveState == "playing" -> MeloAccent
    player.effectiveState == "paused" -> MeloAmber
    else -> MeloBlue
}

private fun providerName(value: String?): String = value?.replace('_', ' ')
    ?.split(' ')?.joinToString(" ") {
        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
    } ?: "Unknown"
