@file:OptIn(ExperimentalTextApi::class)

package com.spoglyadayko.dashboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.spoglyadayko.dashboard.R

// Both are variable fonts (single TTF, full Latin + Cyrillic charset). Weights are selected via the
// `wght` variation axis (FontVariation), which requires API 26+ — matches the app's minSdk.
private fun onestFont(weight: Int) = Font(
    R.font.onest_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun monoFont(weight: Int) = Font(
    R.font.jetbrains_mono_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Display / body font — modern geometric sans with first-class Cyrillic (Ukrainian UI). */
val Onest = FontFamily(
    onestFont(400),
    onestFont(500),
    onestFont(600),
    onestFont(700),
)

/** Monospace family for all numbers, times, scores — instrument-panel feel; full Cyrillic. */
val JetBrainsMono = FontFamily(
    monoFont(400),
    monoFont(500),
    monoFont(700),
)

/** Reuse any Material type role but render it in JetBrains Mono — for times/scores/durations/counts. */
fun TextStyle.mono(): TextStyle = copy(fontFamily = JetBrainsMono)

// Apply Onest to every Material type role, preserving M3's default metrics (size/spacing/line-height).
private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Onest),
    displayMedium = base.displayMedium.copy(fontFamily = Onest),
    displaySmall = base.displaySmall.copy(fontFamily = Onest),
    headlineLarge = base.headlineLarge.copy(fontFamily = Onest),
    headlineMedium = base.headlineMedium.copy(fontFamily = Onest),
    headlineSmall = base.headlineSmall.copy(fontFamily = Onest),
    titleLarge = base.titleLarge.copy(fontFamily = Onest),
    titleMedium = base.titleMedium.copy(fontFamily = Onest),
    titleSmall = base.titleSmall.copy(fontFamily = Onest),
    bodyLarge = base.bodyLarge.copy(fontFamily = Onest),
    bodyMedium = base.bodyMedium.copy(fontFamily = Onest),
    bodySmall = base.bodySmall.copy(fontFamily = Onest),
    labelLarge = base.labelLarge.copy(fontFamily = Onest),
    labelMedium = base.labelMedium.copy(fontFamily = Onest),
    labelSmall = base.labelSmall.copy(fontFamily = Onest),
)
