package com.spoglyadayko.dashboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** Format a Double with a pattern using dot as decimal separator, regardless of device locale. */
fun Double.fmt(pattern: String): String = String.format(Locale.US, pattern, this)

/** Convert a Compose Color to an Android graphics color int for Canvas text paint. */
fun Color.toAndroidColor(): Int = this.toArgb()
