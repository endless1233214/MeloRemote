package com.versarepair.meloremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.versarepair.meloremote.data.model.AuthenticationMode
import com.versarepair.meloremote.data.model.ConnectionState
import com.versarepair.meloremote.ui.MeloRemoteViewModel
import com.versarepair.meloremote.ui.MeloUiState
import com.versarepair.meloremote.ui.components.AppMark

@Composable
fun LoginScreen(state: MeloUiState, viewModel: MeloRemoteViewModel) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark(92)
            Spacer(Modifier.height(24.dp))
            Text("MeloRemote", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Unofficial remote for Music Assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthenticationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.authMode == mode,
                        onClick = { viewModel.setAuthMode(mode) },
                        label = { Text(mode.title) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.serverAddress,
                    onValueChange = viewModel::setServerAddress,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server") },
                    placeholder = { Text("http://musicassistant.local:8095") },
                    leadingIcon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (state.serverAddress.isNotBlank() &&
                    !state.serverAddress.trim().startsWith("https://", ignoreCase = true)
                ) {
                    Text(
                        "This server connection is not encrypted. Use HTTP only on a trusted " +
                            "private network or VPN; prefer HTTPS whenever available.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.authMode == AuthenticationMode.TOKEN) {
                    OutlinedTextField(
                        value = state.longLivedToken,
                        onValueChange = viewModel::setLongLivedToken,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Long-lived token") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                } else {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            val disabled = state.isWorking || state.serverAddress.isBlank() ||
                (state.authMode == AuthenticationMode.TOKEN && state.longLivedToken.isBlank()) ||
                (state.authMode == AuthenticationMode.PASSWORD &&
                    (state.username.isBlank() || state.password.isBlank()))
            Button(
                onClick = viewModel::login,
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting")
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("Sign In")
                }
            }
            if (state.connectionState is ConnectionState.Failed) {
                Spacer(Modifier.height(14.dp))
                Text(
                    state.connectionState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
