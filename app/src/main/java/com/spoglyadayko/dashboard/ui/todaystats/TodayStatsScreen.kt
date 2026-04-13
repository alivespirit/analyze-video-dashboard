package com.spoglyadayko.dashboard.ui.todaystats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.spoglyadayko.dashboard.data.api.AwayInterval
import com.spoglyadayko.dashboard.data.api.ChartEntry
import com.spoglyadayko.dashboard.ui.theme.*
import com.spoglyadayko.dashboard.ui.theme.fmt
import com.spoglyadayko.dashboard.ui.theme.toAndroidColor
import org.koin.androidx.compose.koinViewModel

// All possible statuses in display order
private val ALL_STATUSES = listOf(
    "no_motion", "no_significant_motion", "no_person",
    "significant_motion", "gate_crossing", "error",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayStatsScreen(
    excludedStatuses: Set<String>,
    selectedDay: String?,
    onExcludedChanged: (Set<String>) -> Unit,
    onGateCrossingsClick: () -> Unit,
    viewModel: TodayStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(selectedDay) {
        viewModel.load(selectedDay)
    }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
            }
            state.data != null -> {
                val data = state.data!!
                val filteredChart = if (excludedStatuses.isEmpty()) data.processingChart
                    else data.processingChart.filter { it.status !in excludedStatuses }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${data.day} \u2014 ${data.videosTotal} videos",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    // Status counts — always show all known statuses, tap to exclude
                    item {
                        StatusCountsGrid(
                            counts = data.statusCounts,
                            excludedStatuses = excludedStatuses,
                            onToggle = { status ->
                                val newExcluded = if (status in excludedStatuses) {
                                    excludedStatuses - status
                                } else {
                                    excludedStatuses + status
                                }
                                onExcludedChanged(newExcluded)
                            },
                        )
                    }

                    // Gate crossings + away/back timeline.
                    // Pass "now" only when viewing today, so ongoing intervals end at the
                    // current time and a current-time marker is drawn.
                    item {
                        val today = java.time.LocalDate.now().toString()
                        val nowMinutes = if (data.day == today) {
                            val t = java.time.LocalTime.now()
                            t.hour * 60 + t.minute
                        } else null
                        GateCrossingsCard(
                            counts = data.gateCounts,
                            awayIntervals = data.awayIntervals,
                            nowMinutes = nowMinutes,
                            onClick = onGateCrossingsClick,
                        )
                    }

                    // Processing stats
                    if (data.processingStats.isNotEmpty()) {
                        item { ProcessingStatsCard(data.processingStats) }
                    }

                    // Processing chart (filtered)
                    if (filteredChart.isNotEmpty()) {
                        item { ProcessingChart(filteredChart) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCountsGrid(
    counts: Map<String, Int>,
    excludedStatuses: Set<String>,
    onToggle: (String) -> Unit,
) {
    // Always show all known statuses (even with 0 count), plus any unknown from data
    val unknownStatuses = counts.keys.filter { it !in ALL_STATUSES }.sorted()
    val displayStatuses = ALL_STATUSES + unknownStatuses

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        displayStatuses.forEach { status ->
            val count = counts[status] ?: 0
            val isExcluded = status in excludedStatuses
            val bgColor = statusColor(status)
            val borderMod = if (isExcluded) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(6.dp))
            } else {
                Modifier
            }

            Surface(
                color = if (isExcluded) bgColor.copy(alpha = 0.3f) else bgColor,
                shape = RoundedCornerShape(6.dp),
                modifier = borderMod.clickable { onToggle(status) },
            ) {
                Text(
                    "${status.replace("_", " ")}: $count",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = if (isExcluded) MaterialTheme.typography.labelMedium.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    ) else MaterialTheme.typography.labelMedium,
                    color = if (isExcluded) Color.White.copy(alpha = 0.5f)
                        else if (status in listOf("no_person", "no_significant_motion")) Color.Black
                        else Color.White,
                )
            }
        }
    }
}

@Composable
private fun GateCrossingsCard(
    counts: Map<String, Int>,
    awayIntervals: List<AwayInterval>,
    nowMinutes: Int?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            // Header row (clickable → open gate crossings screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Gate crossings:", fontWeight = FontWeight.Medium)
                Text("\u2191 ${counts["up"] ?: 0}", color = AwayColor)
                Text("\u2193 ${counts["down"] ?: 0}", color = BackColor)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (awayIntervals.isNotEmpty()) {
                HorizontalDivider()
                AwayIntervalsSection(awayIntervals, nowMinutes)
            }
        }
    }
}

/** Parse "HH:MM" to minutes since midnight. Returns null if unparseable. */
private fun parseHhMmToMinutes(s: String?): Int? {
    if (s.isNullOrBlank()) return null
    val parts = s.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

@Composable
private fun AwayIntervalsSection(intervals: List<AwayInterval>, nowMinutes: Int?) {
    val nowMarkerColor = MaterialTheme.colorScheme.tertiary

    // Compute the visible time range:
    //   - left edge: floor(earliest start) to the hour
    //   - right edge: ceil(latest end) to the hour. For ongoing intervals, use `now` when
    //     viewing today (so the bar extends only to the current time), else fall back to 24h.
    val starts = intervals.mapNotNull { parseHhMmToMinutes(it.start) }
    val ends = intervals.mapNotNull { parseHhMmToMinutes(it.end) }
    val hasOpen = intervals.any { it.start != null && it.end == null }

    val earliestStart = starts.minOrNull() ?: 0
    val dayStartMin = (earliestStart / 60) * 60
    val openEndCap = nowMinutes ?: (24 * 60)
    val latestEnd = maxOf(ends.maxOrNull() ?: dayStartMin, if (hasOpen) openEndCap else dayStartMin)
    val dayEndMin = (((latestEnd + 59) / 60) * 60).coerceAtLeast(dayStartMin + 60)
    val totalMin = (dayEndMin - dayStartMin).toFloat().coerceAtLeast(60f)

    Column(modifier = Modifier.padding(12.dp)) {
        Text("Away/Back intervals", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        // Timeline bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
        ) {
            // Background = "home" (subtle)
            drawRect(
                color = BackColor.copy(alpha = 0.12f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height),
            )

            val hatchPaint = android.graphics.Paint().apply {
                color = AwayColor.toAndroidColor()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }

            intervals.forEach { iv ->
                val s = parseHhMmToMinutes(iv.start) ?: return@forEach
                val isOpen = iv.end == null
                // Ongoing intervals end at "now" when viewing today; otherwise the visible right edge.
                val e = parseHhMmToMinutes(iv.end) ?: nowMinutes ?: dayEndMin
                val x1 = (s - dayStartMin) / totalMin * size.width
                val x2 = (e - dayStartMin) / totalMin * size.width
                val w = (x2 - x1).coerceAtLeast(2f)

                if (isOpen) {
                    // Lighter fill + diagonal hatching to indicate "ongoing"
                    drawRect(
                        color = AwayColor.copy(alpha = 0.4f),
                        topLeft = Offset(x1, 0f),
                        size = Size(w, size.height),
                    )
                    // Diagonal hatch lines
                    val step = 6f
                    var d = -size.height
                    while (d < w) {
                        drawContext.canvas.nativeCanvas.drawLine(
                            x1 + d, size.height,
                            x1 + d + size.height, 0f,
                            hatchPaint,
                        )
                        d += step
                    }
                } else {
                    drawRect(
                        color = AwayColor,
                        topLeft = Offset(x1, 0f),
                        size = Size(w, size.height),
                    )
                }
            }

            // Current-time marker (only when viewing today and "now" is in the visible range)
            if (nowMinutes != null && nowMinutes in dayStartMin..dayEndMin) {
                val nowX = (nowMinutes - dayStartMin) / totalMin * size.width
                drawLine(
                    color = nowMarkerColor,
                    start = Offset(nowX, 0f),
                    end = Offset(nowX, size.height),
                    strokeWidth = 2f,
                )
                // Small triangular pointer at the top
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(nowX - 4f, 0f)
                    lineTo(nowX + 4f, 0f)
                    lineTo(nowX, 5f)
                    close()
                }
                drawPath(path = tri, color = nowMarkerColor)
            }
        }

        // Hour labels beneath the timeline
        Spacer(Modifier.height(2.dp))
        val startHour = dayStartMin / 60
        val endHour = dayEndMin / 60
        val spanHours = endHour - startHour
        val step = when {
            spanHours <= 6 -> 1
            spanHours <= 12 -> 2
            else -> 3
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalWidth = maxWidth
            var h = startHour
            while (h <= endHour) {
                val fraction = (h - startHour).toFloat() / spanHours.coerceAtLeast(1)
                Text(
                    "${h}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = totalWidth * fraction - 8.dp),
                )
                h += step
            }
        }

        Spacer(Modifier.height(8.dp))

        // Compact list with arrows
        intervals.forEach { iv ->
            Row(
                modifier = Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u2191", color = AwayColor, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(2.dp))
                Text(iv.start ?: "...", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    "\u2192",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(8.dp))
                if (iv.end != null) {
                    Text("\u2193", color = BackColor, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(2.dp))
                    Text(iv.end, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "ongoing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                iv.dur?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "($it)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingStatsCard(stats: Map<String, Double>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Processing times", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            // Table header
            Row(Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Min", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text("Avg", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text("Max", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Motion detection row
            if (stats.containsKey("md_avg")) {
                Row(Modifier.fillMaxWidth()) {
                    Text("MD", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(stats["md_min"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(stats["md_avg"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(stats["md_max"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Full processing row
            if (stats.containsKey("full_avg")) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Full", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(stats["full_min"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(stats["full_avg"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(stats["full_max"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProcessingChart(chart: List<ChartEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Processing times by video", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            val maxSeconds = chart.maxOfOrNull { it.seconds } ?: 1.0
            val avgSeconds = chart.map { it.seconds }.average()
            // Only use log scale if there's a clear outlier (>= 4x average)
            val useLogScale = avgSeconds > 0 && maxSeconds >= 4.0 * avgSeconds
            val scaleValue: (Double) -> Double = if (useLogScale) {
                { v -> kotlin.math.log10(1.0 + v) }
            } else {
                { v -> v }
            }
            val scaleMax = scaleValue(maxSeconds)

            // Extract hour from each entry's time for hour boundary markers
            val hours = chart.map { it.time?.substringBefore(":")?.toIntOrNull() }

            // Y-axis max label above the chart
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "max: ${maxSeconds.fmt("%.0f")}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (useLogScale) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "log scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            val outlineArgb = MaterialTheme.colorScheme.outline.toAndroidColor()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                val barWidth = size.width / chart.size.coerceAtLeast(1)
                val dashPaint = android.graphics.Paint().apply {
                    color = outlineArgb
                    strokeWidth = 1f
                    style = android.graphics.Paint.Style.STROKE
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
                }

                // Draw hour boundary lines
                var lastHour: Int? = null
                chart.forEachIndexed { i, _ ->
                    val h = hours[i]
                    if (h != null && h != lastHour && lastHour != null) {
                        val x = i * barWidth
                        drawContext.canvas.nativeCanvas.drawLine(x, 0f, x, size.height, dashPaint)
                    }
                    lastHour = h ?: lastHour
                }

                // Draw bars
                chart.forEachIndexed { i, entry ->
                    val scaledVal = scaleValue(entry.seconds)
                    val barHeight = (scaledVal / scaleMax * size.height).toFloat()
                    drawRect(
                        color = statusColor(entry.status),
                        topLeft = Offset(i * barWidth, size.height - barHeight),
                        size = Size(barWidth - 1f, barHeight),
                    )
                }
            }

            // Hour labels below the chart
            Spacer(Modifier.height(2.dp))
            val hourBoundaries = mutableListOf<Pair<Int, Float>>() // hour to fraction
            run {
                var lastH: Int? = null
                chart.forEachIndexed { i, _ ->
                    val h = hours[i]
                    if (h != null && h != lastH && lastH != null) {
                        hourBoundaries.add(h to i.toFloat() / chart.size.coerceAtLeast(1))
                    }
                    lastH = h ?: lastH
                }
            }
            if (hourBoundaries.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val totalWidth = maxWidth
                    hourBoundaries.forEach { (h, fraction) ->
                        Text(
                            "${h}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = totalWidth * fraction - 8.dp),
                        )
                    }
                }
            }
        }
    }
}

