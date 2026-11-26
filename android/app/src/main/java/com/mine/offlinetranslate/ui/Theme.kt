package com.mine.offlinetranslate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EFE7),
    onPrimaryContainer = Color(0xFF06251F),
    secondary = Color(0xFFC05621),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF1D1B17),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF5B554A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8C4),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0B4F43),
    onPrimaryContainer = Color(0xFFB8F2E4),
    secondary = Color(0xFFFFB27A),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE8E3D8),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE8E3D8),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFB9B3A7),
)

@Composable
fun OfflineTranslateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
