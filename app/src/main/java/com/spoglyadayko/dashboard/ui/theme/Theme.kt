package com.spoglyadayko.dashboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6B52FA),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFF65E0FF),
    background = Color(0xFF0E1014),
    surface = Color(0xFF191C22),
    surfaceVariant = Color(0xFF24282F),
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

// MaterialExpressiveTheme applies M3 Expressive defaults (expressive MotionScheme + shapes) on top
// of our custom brand colors. Opt-in is required while the Expressive API is experimental in
// material3 1.5.0-alpha18. Motion/shapes default to expressive values; override here later if needed.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpoglyadaykoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    // On Android 12+ adopt the system (Material You) accent for primary-coloured controls — the nav
    // pill, date picker, buttons — while keeping our custom surfaces/background and status colors.
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        val dynamic = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        base.copy(
            primary = dynamic.primary,
            onPrimary = dynamic.onPrimary,
            primaryContainer = dynamic.primaryContainer,
            onPrimaryContainer = dynamic.onPrimaryContainer,
            inversePrimary = dynamic.inversePrimary,
        )
    } else {
        base
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

// Subtle full-screen gradient for the app background — a faint primary tint at the top easing into
// the base background, with a faint secondary tint at the very bottom (behind the floating nav).
// Shows only in the slivers cards don't cover, adding depth without touching readability.
@Composable
fun appBackgroundBrush(): Brush {
    val base = MaterialTheme.colorScheme.background
    // Tint with fixed brand cools (cyan/green) — NOT colorScheme.primary, which is the Material You
    // system accent and would bleed the wallpaper's (possibly warm) hue into the background.
    return Brush.verticalGradient(
        listOf(
            lerp(base, MaterialTheme.colorScheme.tertiary, 0.06f),
            base,
            base,
            lerp(base, MaterialTheme.colorScheme.secondary, 0.05f),
        ),
    )
}
