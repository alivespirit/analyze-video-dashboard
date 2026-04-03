package com.spoglyadayko.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B52FA),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFF65E0FF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFF22252B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444),
    outline = Color(0xFF334155),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6B52FA),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFEF4444),
    outline = Color(0xFFCBD5E1),
)

@Composable
fun SpoglyadaykoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
