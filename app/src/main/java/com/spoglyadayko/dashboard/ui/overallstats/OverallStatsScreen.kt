package com.spoglyadayko.dashboard.ui.overallstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoglyadayko.dashboard.data.api.DayStats
import com.spoglyadayko.dashboard.data.api.EventsHeatmap
import com.spoglyadayko.dashboard.data.api.WeekdayHeatmap
import com.spoglyadayko.dashboard.ui.theme.*
import com.spoglyadayko.dashboard.ui.theme.fmt
import com.spoglyadayko.dashboard.ui.theme.toAndroidColor
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallStatsScreen(viewModel: OverallStatsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
            }
            state.data != null -> {
                val data = state.data!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Per-day video count chart
                    if (data.perDay.isNotEmpty()) {
                        item { PerDayChart(data.perDay) }
                    }

                    // Processing times chart
                    if (data.perDay.any { it.mdAvg != null || it.fullAvg != null }) {
                        item { ProcessingTimesChart(data.perDay) }
                    }

                    // Weekday heatmaps
                    item {
                        WeekdayHeatmapCard(
                            title = "Away by weekday",
                            heatmap = data.weekdayHeatmap,
                            isAway = true,
                        )
                    }
                    item {
                        WeekdayHeatmapCard(
                            title = "Back by weekday",
                            heatmap = data.weekdayHeatmap,
                            isAway = false,
                        )
                    }
                }
            }
            !state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(title: String, heatmap: EventsHeatmap) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                "${heatmap.daysCount} days analyzed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val maxVal = maxOf(
                heatmap.awayDays.maxOrNull() ?: 0,
                heatmap.backDays.maxOrNull() ?: 0,
                1,
            )

            // Away row
            Text("Away", style = MaterialTheme.typography.labelSmall, color = AwayColor)
            HeatmapRow(
                values = heatmap.awayDays,
                maxVal = maxVal,
                color = AwayColor,
                binsPerHour = 60 / heatmap.binMinutes,
                startHour = heatmap.startOffset / 60,
            )
            Spacer(Modifier.height(4.dp))

            // Back row
            Text("Back", style = MaterialTheme.typography.labelSmall, color = BackColor)
            HeatmapRow(
                values = heatmap.backDays,
                maxVal = maxVal,
                color = BackColor,
                binsPerHour = 60 / heatmap.binMinutes,
                startHour = heatmap.startOffset / 60,
            )

            Spacer(Modifier.height(4.dp))
            // Hour labels
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                val hours = (heatmap.startOffset / 60) until (heatmap.startOffset / 60 + heatmap.bins / (60 / heatmap.binMinutes))
                hours.forEach { h ->
                    Text(
                        "${h}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width((300.dp / hours.count()).coerceAtLeast(16.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapRow(
    values: List<Int>,
    maxVal: Int,
    color: Color,
    binsPerHour: Int,
    startHour: Int,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        val cellWidth = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, v ->
            val alpha = if (maxVal > 0) v.toFloat() / maxVal else 0f
            drawRect(
                color = color.copy(alpha = alpha.coerceIn(0.05f, 1f)),
                topLeft = Offset(i * cellWidth, 0f),
                size = Size(cellWidth - 1f, size.height),
            )
        }
    }
}

@Composable
private fun PerDayChart(perDay: List<DayStats>) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Videos per day", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toAndroidColor()
            val maxVideos = perDay.maxOfOrNull { it.videosTotal } ?: 1
            val allStatuses = perDay.flatMap { it.statusCounts.keys }.distinct()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(perDay.size) {
                        detectTapGestures { offset ->
                            val barWidth = size.width.toFloat() / perDay.size.coerceAtLeast(1)
                            val idx = (offset.x / barWidth).toInt().coerceIn(0, perDay.size - 1)
                            selectedIdx = if (selectedIdx == idx) null else idx
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / perDay.size.coerceAtLeast(1)

                    perDay.forEachIndexed { i, day ->
                        var yOffset = size.height
                        allStatuses.forEach { status ->
                            val count = day.statusCounts[status] ?: 0
                            if (count > 0) {
                                val barHeight = (count.toFloat() / maxVideos * size.height)
                                yOffset -= barHeight
                                drawRect(
                                    color = statusColor(status),
                                    topLeft = Offset(i * barWidth, yOffset),
                                    size = Size(barWidth - 1f, barHeight),
                                )
                            }
                        }

                        // Selection highlight
                        if (selectedIdx == i) {
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(i * barWidth, 0f),
                                size = Size(barWidth - 1f, size.height),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                            )
                        }
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        "$maxVideos",
                        4f, 14f,
                        android.graphics.Paint().apply {
                            this.color = labelArgb
                            textSize = 24f
                        },
                    )
                }
            }

            // Day labels
            Spacer(Modifier.height(4.dp))
            val lastDays = perDay.takeLast(7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                lastDays.forEach {
                    Text(
                        it.day.takeLast(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selected day details
            selectedIdx?.let { idx ->
                val day = perDay.getOrNull(idx) ?: return@let
                Spacer(Modifier.height(6.dp))
                Text(
                    "${day.day}  \u2022  ${day.videosTotal} videos",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val breakdown = day.statusCounts.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key.replace("_", " ")}: ${it.value}" }
                Text(
                    breakdown,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                day.mdAvg?.let {
                    Text(
                        "MD avg: ${it.fmt("%.1f")}s" + (day.fullAvg?.let { f -> "  \u2022  Full avg: ${f.fmt("%.1f")}s" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingTimesChart(perDay: List<DayStats>) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    val mdColor = Color(0xFF9CA3AF) // gray for MD
    val fullColor = Color(0xFF3B82F6) // blue for Full

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Processing times per day", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(mdColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text("MD avg", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(fullColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text("Full avg", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toAndroidColor()
            val maxTime = perDay.maxOf {
                maxOf(it.mdAvg ?: 0.0, it.fullAvg ?: 0.0)
            }.coerceAtLeast(1.0)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(perDay.size) {
                        detectTapGestures { offset ->
                            val barWidth = size.width.toFloat() / perDay.size.coerceAtLeast(1)
                            val idx = (offset.x / barWidth).toInt().coerceIn(0, perDay.size - 1)
                            selectedIdx = if (selectedIdx == idx) null else idx
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / perDay.size.coerceAtLeast(1)
                    val halfBar = barWidth / 2f

                    perDay.forEachIndexed { i, day ->
                        // MD bar (left half)
                        val mdH = ((day.mdAvg ?: 0.0) / maxTime * size.height).toFloat()
                        if (mdH > 0) {
                            drawRect(
                                color = mdColor,
                                topLeft = Offset(i * barWidth, size.height - mdH),
                                size = Size(halfBar - 1f, mdH),
                            )
                        }
                        // Full bar (right half)
                        val fullH = ((day.fullAvg ?: 0.0) / maxTime * size.height).toFloat()
                        if (fullH > 0) {
                            drawRect(
                                color = fullColor,
                                topLeft = Offset(i * barWidth + halfBar, size.height - fullH),
                                size = Size(halfBar - 1f, fullH),
                            )
                        }

                        if (selectedIdx == i) {
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(i * barWidth, 0f),
                                size = Size(barWidth - 1f, size.height),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                            )
                        }
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        "${maxTime.fmt("%.0f")}s",
                        4f, 14f,
                        android.graphics.Paint().apply {
                            this.color = labelArgb
                            textSize = 24f
                        },
                    )
                }
            }

            // Day labels
            Spacer(Modifier.height(4.dp))
            val lastDays = perDay.takeLast(7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                lastDays.forEach {
                    Text(
                        it.day.takeLast(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selected day details
            selectedIdx?.let { idx ->
                val day = perDay.getOrNull(idx) ?: return@let
                Spacer(Modifier.height(6.dp))
                Text(
                    day.day,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val parts = mutableListOf<String>()
                day.mdAvg?.let { parts.add("MD avg: ${it.fmt("%.1f")}s") }
                day.fullAvg?.let { parts.add("Full avg: ${it.fmt("%.1f")}s") }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString("  \u2022  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeatmapCard(title: String, heatmap: WeekdayHeatmap, isAway: Boolean) {
    val counts = if (isAway) heatmap.awayCounts else heatmap.backCounts
    val color = if (isAway) AwayColor else BackColor
    val maxVal = counts.flatten().maxOrNull() ?: 1

    // Selected cell state: weekday index + bin index
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            heatmap.weekdayLabels.forEachIndexed { wd, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .pointerInput(wd) {
                                detectTapGestures { offset ->
                                    val bins = counts.getOrNull(wd) ?: return@detectTapGestures
                                    val cellWidth = size.width.toFloat() / bins.size.coerceAtLeast(1)
                                    val binIdx = (offset.x / cellWidth).toInt().coerceIn(0, bins.size - 1)
                                    selectedCell = if (selectedCell == Pair(wd, binIdx)) null else Pair(wd, binIdx)
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val bins = counts.getOrNull(wd) ?: return@Canvas
                            val cellWidth = size.width / bins.size.coerceAtLeast(1)
                            bins.forEachIndexed { i, v ->
                                val alpha = if (maxVal > 0) v.toFloat() / maxVal else 0f
                                val isSelected = selectedCell == Pair(wd, i)
                                drawRect(
                                    color = color.copy(alpha = alpha.coerceIn(0.05f, 1f)),
                                    topLeft = Offset(i * cellWidth, 0f),
                                    size = Size(cellWidth - 1f, size.height),
                                )
                                if (isSelected) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(i * cellWidth, 0f),
                                        size = Size(cellWidth - 1f, size.height),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(32.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (h in heatmap.startOffset / 60..heatmap.startOffset / 60 + heatmap.bins step 3) {
                        Text("${h}h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Selected cell details
            selectedCell?.let { (wd, binIdx) ->
                val count = counts.getOrNull(wd)?.getOrNull(binIdx) ?: 0
                val totalDays = heatmap.weekdayDayCounts.getOrNull(wd) ?: 0
                val startHour = heatmap.startOffset / 60 + binIdx * heatmap.binMinutes / 60
                val startMin = (binIdx * heatmap.binMinutes) % 60
                val endHour = startHour + heatmap.binMinutes / 60
                val pct = if (totalDays > 0) (count.toFloat() / totalDays * 100).toDouble().fmt("%.0f") else "0"
                Spacer(Modifier.height(6.dp))
                Text(
                    "${heatmap.weekdayLabels.getOrNull(wd) ?: "?"} ${"%02d".format(startHour)}:${"%02d".format(startMin)}\u2013${"%02d".format(endHour)}:${"%02d".format(startMin)}  \u2022  $count/$totalDays days ($pct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}
