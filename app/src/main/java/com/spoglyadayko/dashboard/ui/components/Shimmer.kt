package com.spoglyadayko.dashboard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A horizontally-sweeping gradient brush for skeleton placeholders. One transition drives all boxes
// that share the returned brush, so the whole skeleton shimmers in sync.
@Composable
fun rememberShimmerBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTranslate",
    )
    return Brush.linearGradient(
        colors = listOf(
            scheme.surfaceVariant,
            scheme.onSurfaceVariant.copy(alpha = 0.20f),
            scheme.surfaceVariant,
        ),
        start = Offset(translate, 0f),
        end = Offset(translate + 300f, 0f),
    )
}

@Composable
fun ShimmerBox(brush: Brush, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
    )
}

/** Card-shaped shimmer placeholders for an initial list/screen load. */
@Composable
fun ShimmerList(modifier: Modifier = Modifier, rows: Int = 8, rowHeight: Dp = 56.dp) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) {
            ShimmerBox(brush = brush, height = rowHeight)
        }
    }
}
