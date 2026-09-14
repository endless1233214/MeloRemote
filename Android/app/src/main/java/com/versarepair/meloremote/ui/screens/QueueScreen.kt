package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.MAQueueItem
import com.versarepair.meloremote.data.model.formattedDuration
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.components.Artwork
import com.versarepair.meloremote.ui.components.MediaActionsButton
import com.versarepair.meloremote.ui.theme.MeloAccent

@Composable
fun QueueScreen(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    val current = state.activeQueue?.currentItem
    val upNext = if (current == null) {
        state.queueItems
    } else {
        val currentPosition = state.queueItems.indexOfFirst { it.queueItemId == current.queueItemId }
        if (currentPosition >= 0) state.queueItems.drop(currentPosition + 1)
        else state.queueItems.filterNot { it.queueItemId == current.queueItemId }
    }
    if (current == null && state.queueItems.isEmpty()) {
        EmptyMessage("Queue Is Empty", "Add music from the Library tab.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (current != null) {
            item("playing-heading") { SectionTitle("Playing") }
            item("playing-${current.queueItemId}") {
                QueueRow(current, true, viewModel)
            }
        }
        item("up-next-heading") { SectionTitle("Up Next") }
        items(upNext, key = { it.queueItemId }) { item ->
            QueueRow(item, false, viewModel)
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun QueueRow(item: MAQueueItem, isCurrent: Boolean, viewModel: MeloRemoteViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { viewModel.playQueueItem(item) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(
                url = viewModel.imageUrl(item, 160),
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                cornerRadius = 6,
            )
            if (isCurrent) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp),
                    color = MeloAccent,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Currently playing",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
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
        item.duration?.let {
            Text(it.formattedDuration(), style = MaterialTheme.typography.labelSmall)
        }
        item.mediaItem?.let { MediaActionsButton(it, viewModel, includePlayback = false) }
        if (!isCurrent) {
            IconButton(onClick = { viewModel.removeQueueItem(item) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${item.title} from queue")
            }
        }
    }
}
