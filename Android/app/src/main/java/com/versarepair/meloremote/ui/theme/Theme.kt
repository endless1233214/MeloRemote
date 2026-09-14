package com.versarepair.meloremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MeloAccent = Color(0xFFE84A40)
val MeloMint = Color(0xFF26A884)
val MeloBlue = Color(0xFF3D7DDB)
val MeloAmber = Color(0xFFEA9E2B)

private val LightColors = lightColorScheme(
    primary = MeloAccent,
    secondary = MeloMint,
    tertiary = MeloBlue,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B61),
    secondary = Color(0xFF49C7A5),
    tertiary = Color(0xFF77A7F4),
)

@Composable
fun MeloRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
