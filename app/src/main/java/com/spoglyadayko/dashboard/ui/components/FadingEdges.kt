package com.spoglyadayko.dashboard.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Fades the content to transparent within `topFade`/`bottomFade` of each edge, so a scrolling list's
// items melt out at the top and in at the bottom. Implemented as an offscreen-composited DstIn mask
// (a vertical alpha gradient), so it's container-level — no per-item alpha or recycling concerns.
// A soft highlight band that sweeps top→bottom as `progress` goes 0→1, drawn over the content
// (non-destructive — the list stays fully visible). Used as a "flow" when a list becomes active.
// `progress` is a lambda so the modifier stays stable and re-reads the animated value each frame.
fun Modifier.sheenSweep(progress: () -> Float): Modifier = this.drawWithContent {
    drawContent()
    val p = progress()
    if (p > 0f && p < 1f) {
        val band = size.height * 0.5f
        val top = p * (size.height + band) - band
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.22f),
                1f to Color.Transparent,
                startY = top,
                endY = top + band,
            ),
        )
    }
}

fun Modifier.fadingEdges(topFade: Dp = 0.dp, bottomFade: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topPx = topFade.toPx()
        val bottomPx = bottomFade.toPx()
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = topPx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - bottomPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
