package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.components.AppMark
import com.versarepair.meloremote.ui.components.ConnectionPill
import com.versarepair.meloremote.ui.theme.MeloMint

@Composable
fun SettingsScreen(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val uriHandler = LocalUriHandler.current
    var refreshing by remember { mutableStateOf(false) }
    var refreshSucceeded by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppMark(54)
                Column {
                    Text("MeloRemote", fontWeight = FontWeight.Bold)
                    Text(
                        "Unofficial Music Assistant client",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectionPill(state.connectionState)
                }
            }
        }
        SettingsSection("Server") {
            DetailRow("Address", state.serverAddress)
            DetailRow("Version", state.serverInfo?.serverVersion ?: "Unknown")
            DetailRow("API Schema", state.serverInfo?.schemaVersion?.toString() ?: "Unknown")
        }
        SettingsSection("Account") {
            DetailRow("User", state.currentUser?.title ?: state.username)
            state.currentUser?.role?.let {
                DetailRow("Role", it.replaceFirstChar { char -> char.titlecase(locale) })
            }
        }
        SettingsSection("About") {
            TextButton(
                onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Privacy Policy")
            }
            TextButton(
                onClick = { uriHandler.openUri(SUPPORT_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Support")
            }
        }
        Button(
            onClick = {
                if (!refreshing) {
                    refreshing = true
                    refreshSucceeded = false
                    viewModel.refresh { success ->
                        refreshing = false
                        refreshSucceeded = success
                    }
                }
            },
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (refreshing) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (refreshing) "Refreshing Server Data…" else "Refresh Server Data")
        }
        if (refreshSucceeded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MeloMint)
                Text("Server data refreshed", color = MeloMint)
            }
        }
        OutlinedButton(onClick = { showSignOut = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You’ll need to sign in again to reconnect to this Music Assistant server.") },
            dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    viewModel.logout()
                }) { Text("Sign Out") }
            },
        )
    }
}

private const val PRIVACY_POLICY_URL =
    "https://versarepair.com/app-development/privacy-policies/meloremote/"
private const val SUPPORT_URL = "https://versarepair.com/app-development/support/"

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value.ifBlank { "Unknown" },
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
