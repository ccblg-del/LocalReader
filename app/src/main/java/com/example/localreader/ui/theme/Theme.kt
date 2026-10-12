package com.example.localreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Secondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFEFEFF3)
)

private val DarkColors = darkColorScheme(
    primary = Secondary,
    onPrimary = Color.White,
    secondary = Secondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF2A2A2A)
)

@Composable
fun LocalReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
