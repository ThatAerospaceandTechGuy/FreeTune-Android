package com.freetune.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A6FF),
    onPrimary = Color(0xFF1A0B4D),
    primaryContainer = Color(0xFF3A2A7A),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFF8FD5FF),
    onSecondary = Color(0xFF00344A),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFECEAFF),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFECEAFF),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFB9B6C8),
    outline = Color(0xFF4A4858),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A3FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1A0B4D),
    secondary = Color(0xFF00658A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE8E4F5),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF79757F),
    error = Color(0xFFBA1A1A)
)

@Composable
fun FreeTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
