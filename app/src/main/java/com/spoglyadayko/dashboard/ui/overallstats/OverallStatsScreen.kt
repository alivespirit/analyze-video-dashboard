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
import com.spoglyadayko.dashboard.data.api.ReIDDayStats
import com.spoglyadayko.dashboard.data.api.ReIDMovingAvgPoint
import com.spoglyadayko.dashboard.data.api.ReIDStatsResponse
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
                val reidRange by viewModel.reidRange.collectAsState()
                val perDayRange by viewModel.perDayRange.collectAsState()
                val processingRange by viewModel.processingRange.collectAsState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ReID accuracy card (first item; only if backend returned data)
                    state.reid?.let { reid ->
                        if (reid.perDay.isNotEmpty()) {
                            item {
                                ReIDAccuracyCard(
                                    reid = reid,
                                    rangeLabel = reidRange,
                                    onRangeChange = viewModel::setReidRange,
                                )
                            }
                        }
                    }

                    // Per-day video count chart
                    if (data.perDay.isNotEmpty()) {
                        item {
                            PerDayChart(
                                perDayAll = data.perDay,
                                rangeLabel = perDayRange,
                                onRangeChange = viewModel::setPerDayRange,
                            )
                        }
                    }

                    // Processing times chart
                    if (data.perDay.any { it.mdAvg != null || it.fullAvg != null }) {
                        item {
                            ProcessingTimesChart(
                                perDayAll = data.perDay,
                                rangeLabel = processingRange,
                                onRangeChange = viewModel::setProcessingRange,
                            )
                        }
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
private fun PerDayChart(
    perDayAll: List<DayStats>,
    rangeLabel: String,
    onRangeChange: (String) -> Unit,
) {
    val rangeOptions = remember {
        listOf(RangeOption("7d", 7), RangeOption("30d", 30), RangeOption("All", null))
    }
    val range = rangeOptions.find { it.label == rangeLabel } ?: rangeOptions[1]
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rangeLabel) { selectedIdx = null }
    val perDay = perDayAll.lastDays(range.days)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Videos per day", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                RangeChips(
                    options = rangeOptions,
                    selected = range,
                    onSelect = { if (it.label != range.label) onRangeChange(it.label) },
                )
            }
            Spacer(Modifier.height(8.dp))

            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toAndroidColor()
            val maxVideos = perDay.maxOfOrNull { it.videosTotal } ?: 1
            // Canonical stack order, bottom-up — drawing starts from bottom so the
            // first entry sits at the base. Order from "noisiest / least significant"
            // to "most significant", with anything unrecognised at the very top.
            val statusOrder = listOf(
                "no_motion",
                "no_significant_motion",
                "no_person",
                "significant_motion",
                "gate_crossing",
                "error",
                "unknown",
            )
            val seen = perDay.flatMap { it.statusCounts.keys }.toSet()
            val allStatuses =
                statusOrder.filter { it in seen } + (seen - statusOrder.toSet()).sorted()

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

            // Day labels (evenly spaced across all days)
            Spacer(Modifier.height(4.dp))
            DayLabelsRow(perDay.map { it.day })

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
                        "Motion Detection avg: ${it.fmt("%.1f")}s" + (day.fullAvg?.let { f -> "  \u2022  Full Processing avg: ${f.fmt("%.1f")}s" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingTimesChart(
    perDayAll: List<DayStats>,
    rangeLabel: String,
    onRangeChange: (String) -> Unit,
) {
    val rangeOptions = remember {
        listOf(RangeOption("7d", 7), RangeOption("30d", 30), RangeOption("All", null))
    }
    val range = rangeOptions.find { it.label == rangeLabel } ?: rangeOptions[1]
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rangeLabel) { selectedIdx = null }
    var showMd by remember { mutableStateOf(true) }
    var showFull by remember { mutableStateOf(true) }
    val mdColor = Color(0xFF9CA3AF) // gray for MD
    val fullColor = Color(0xFF3B82F6) // blue for Full
    val perDay = perDayAll.lastDays(range.days)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Processing times per day", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                RangeChips(
                    options = rangeOptions,
                    selected = range,
                    onSelect = { if (it.label != range.label) onRangeChange(it.label) },
                )
            }
            Spacer(Modifier.height(4.dp))
            // Legend (clickable to toggle series visibility)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendChip(
                    label = "Motion Detection avg",
                    color = mdColor,
                    selected = showMd,
                    // Don't allow turning off both at once.
                    onClick = { if (showFull || !showMd) showMd = !showMd },
                )
                LegendChip(
                    label = "Full Processing avg",
                    color = fullColor,
                    selected = showFull,
                    onClick = { if (showMd || !showFull) showFull = !showFull },
                )
            }
            Spacer(Modifier.height(8.dp))

            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toAndroidColor()
            val maxTime = perDay.maxOf {
                val md = if (showMd) it.mdAvg ?: 0.0 else 0.0
                val full = if (showFull) it.fullAvg ?: 0.0 else 0.0
                maxOf(md, full)
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
                    val bothShown = showMd && showFull
                    val subBar = if (bothShown) barWidth / 2f else barWidth

                    perDay.forEachIndexed { i, day ->
                        var offsetX = i * barWidth
                        if (showMd) {
                            val mdH = ((day.mdAvg ?: 0.0) / maxTime * size.height).toFloat()
                            if (mdH > 0) {
                                drawRect(
                                    color = mdColor,
                                    topLeft = Offset(offsetX, size.height - mdH),
                                    size = Size(subBar - 1f, mdH),
                                )
                            }
                            if (bothShown) offsetX += subBar
                        }
                        if (showFull) {
                            val fullH = ((day.fullAvg ?: 0.0) / maxTime * size.height).toFloat()
                            if (fullH > 0) {
                                drawRect(
                                    color = fullColor,
                                    topLeft = Offset(offsetX, size.height - fullH),
                                    size = Size(subBar - 1f, fullH),
                                )
                            }
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

            // Day labels (evenly spaced across all days)
            Spacer(Modifier.height(4.dp))
            DayLabelsRow(perDay.map { it.day })

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
                if (showMd) day.mdAvg?.let { parts.add("Motion Detection avg: ${it.fmt("%.1f")}s") }
                if (showFull) day.fullAvg?.let { parts.add("Full Processing avg: ${it.fmt("%.1f")}s") }
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

// Day-range filter for per-day charts. Null = all days.
private data class RangeOption(val label: String, val days: Int?)

@Composable
private fun RangeChips(
    options: List<RangeOption>,
    selected: RangeOption,
    onSelect: (RangeOption) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { opt ->
            val isSelected = opt.label == selected.label
            val bg = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(10.dp))
                    .pointerInput(isSelected) { detectTapGestures(onTap = { onSelect(opt) }) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(opt.label, style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

// Slice a list to the last N entries, or return as-is when days is null.
private fun <T> List<T>.lastDays(days: Int?): List<T> =
    if (days == null || size <= days) this else this.subList(size - days, size)

@Composable
private fun LegendChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = if (selected) color else color.copy(alpha = 0.25f)
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pointerInput(selected) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 2.dp),
    ) {
        Box(Modifier.size(10.dp).background(swatchColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun WeekdayHeatmapCard(title: String, heatmap: WeekdayHeatmap, isAway: Boolean) {
    val color = if (isAway) AwayColor else BackColor

    // Find first bin with any event across both away and back (shared trim for both cards)
    val allCounts = heatmap.awayCounts + heatmap.backCounts
    val trimStart = allCounts
        .mapNotNull { bins -> bins.indexOfFirst { it > 0 }.takeIf { it >= 0 } }
        .minOrNull() ?: 0

    val counts = if (isAway) heatmap.awayCounts else heatmap.backCounts
    val trimmedCounts = counts.map { it.drop(trimStart) }
    val maxVal = trimmedCounts.flatten().maxOrNull() ?: 1

    // Selected cell state: weekday index + bin index (in trimmed space)
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            heatmap.weekdayLabels.forEachIndexed { wd, label ->
                val bins = trimmedCounts.getOrNull(wd) ?: emptyList()
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
                                    if (bins.isEmpty()) return@detectTapGestures
                                    val cellWidth = size.width.toFloat() / bins.size.coerceAtLeast(1)
                                    val binIdx = (offset.x / cellWidth).toInt().coerceIn(0, bins.size - 1)
                                    selectedCell = if (selectedCell == Pair(wd, binIdx)) null else Pair(wd, binIdx)
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (bins.isEmpty()) return@Canvas
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

            // Hour labels (adjusted for trim)
            val trimmedStartOffset = heatmap.startOffset + trimStart * heatmap.binMinutes
            val trimmedBins = heatmap.bins - trimStart
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(32.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (h in trimmedStartOffset / 60..trimmedStartOffset / 60 + trimmedBins step 3) {
                        Text("${h}h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Selected cell details (map trimmed index back to original time)
            selectedCell?.let { (wd, binIdx) ->
                val count = trimmedCounts.getOrNull(wd)?.getOrNull(binIdx) ?: 0
                val totalDays = heatmap.weekdayDayCounts.getOrNull(wd) ?: 0
                val origBinIdx = binIdx + trimStart
                val startHour = heatmap.startOffset / 60 + origBinIdx * heatmap.binMinutes / 60
                val startMin = (origBinIdx * heatmap.binMinutes) % 60
                val endHour = startHour + heatmap.binMinutes / 60
                val pct = if (totalDays > 0) (count.toFloat() / totalDays * 100).toDouble().fmt("%.0f") else "0"
                Spacer(Modifier.height(6.dp))
                Text(
                    "${heatmap.weekdayLabels.getOrNull(wd) ?: "?"} ${"%02d".format(startHour)}:${"%02d".format(startMin)}\u2013${"%02d".format(endHour)}:${"%02d".format(startMin)}  \u2022  $count/$totalDays days ($pct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )

                // Per-occurrence list (date \u2014 time), mapped back from the trimmed bin index.
                val occurrencesAll = if (isAway) heatmap.awayOccurrences else heatmap.backOccurrences
                val occurrences = occurrencesAll.getOrNull(wd)?.getOrNull(origBinIdx).orEmpty()
                if (occurrences.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        occurrences.forEach { occ ->
                            val dateShort = occ.date.takeLast(5) // MM-DD
                            val timeShort = occ.hhmmss.take(5)   // HH:MM
                            Text(
                                if (timeShort.isNotEmpty()) "$dateShort  \u2022  $timeShort" else dateShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReIDAccuracyCard(
    reid: ReIDStatsResponse,
    rangeLabel: String,
    onRangeChange: (String) -> Unit,
) {
    val precisionColor = Color(0xFF3B82F6) // blue
    val recallColor = Color(0xFFF59E0B)    // amber
    val selectionColor = MaterialTheme.colorScheme.primary
    val rangeOptions = remember {
        listOf(
            RangeOption("7d", 7),
            RangeOption("30d", 30),
            RangeOption("90d", 90),
            RangeOption("All", null),
        )
    }
    val range = rangeOptions.find { it.label == rangeLabel } ?: rangeOptions[1]
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rangeLabel) { selectedIdx = null }
    var helpExpanded by remember { mutableStateOf(false) }
    var showScore by remember { mutableStateOf(false) }
    val scoreColor = Color(0xFF14B8A6) // teal — distinct from precision/recall

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ReID Accuracy", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { helpExpanded = !helpExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(if (helpExpanded) "Hide" else "What's this?", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (helpExpanded) {
                Spacer(Modifier.height(4.dp))
                ReIDHelpBlock()
            }
            Spacer(Modifier.height(10.dp))

            // Big totals row: Precision / Recall / F1 / Match score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReIDMetricBlock("Precision", reid.totals.precision, precisionColor)
                ReIDMetricBlock("Recall", reid.totals.recall, recallColor)
                ReIDMetricBlock("F1", reid.totals.f1, MaterialTheme.colorScheme.primary)
                ReIDMetricBlock("Score", reid.totals.scoreAvg, scoreColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "TP: ${reid.totals.tp}  •  FP: ${reid.totals.fp}  •  FN: ${reid.totals.fn}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(12.dp))

            // Legend (Match score is tap-to-toggle; others are static).
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(precisionColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text("Precision", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(recallColor, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text("Recall", style = MaterialTheme.typography.labelSmall)
                }
                LegendChip(
                    label = "Match score (dashed)",
                    color = scoreColor,
                    selected = showScore,
                    onClick = { showScore = !showScore },
                )
            }
            Text(
                "7-day MA bold, daily faint.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Spacer(Modifier.height(6.dp))
            RangeChips(
                options = rangeOptions,
                selected = range,
                onSelect = { if (it.label != range.label) onRangeChange(it.label) },
            )
            Spacer(Modifier.height(6.dp))

            val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toAndroidColor()
            val perDay = reid.perDay.lastDays(range.days)
            val movingAvg = reid.movingAvg7d.lastDays(range.days)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(perDay.size) {
                        detectTapGestures { offset ->
                            if (perDay.isEmpty()) return@detectTapGestures
                            val step = size.width.toFloat() / perDay.size.coerceAtLeast(1)
                            val idx = (offset.x / step).toInt().coerceIn(0, perDay.size - 1)
                            selectedIdx = if (selectedIdx == idx) null else idx
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (perDay.isEmpty()) return@Canvas
                    val w = size.width
                    val h = size.height
                    val step = w / perDay.size.coerceAtLeast(1)
                    val xCenter = { i: Int -> i * step + step / 2f }

                    // Gridlines at y=0.25, 0.5, 0.75, 1.0 (values map: 1.0 -> top, 0 -> bottom)
                    val gridColor = Color.White.copy(alpha = 0.12f)
                    listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { v ->
                        val y = h - (v * h)
                        drawRect(
                            color = gridColor,
                            topLeft = Offset(0f, y),
                            size = Size(w, 1f),
                        )
                    }

                    // Daily faint lines
                    drawSeries(
                        values = perDay.map { it.precision },
                        xCenter = xCenter,
                        height = h,
                        color = precisionColor.copy(alpha = 0.30f),
                        strokeWidth = 2f,
                    )
                    drawSeries(
                        values = perDay.map { it.recall },
                        xCenter = xCenter,
                        height = h,
                        color = recallColor.copy(alpha = 0.30f),
                        strokeWidth = 2f,
                    )
                    if (showScore) {
                        drawSeriesDashed(
                            values = perDay.map { it.scoreAvg },
                            xCenter = xCenter,
                            height = h,
                            color = scoreColor.copy(alpha = 0.30f),
                            strokeWidth = 2f,
                        )
                    }

                    // 7-day moving average (bold)
                    drawSeries(
                        values = movingAvg.map { it.precision },
                        xCenter = xCenter,
                        height = h,
                        color = precisionColor,
                        strokeWidth = 4f,
                    )
                    drawSeries(
                        values = movingAvg.map { it.recall },
                        xCenter = xCenter,
                        height = h,
                        color = recallColor,
                        strokeWidth = 4f,
                    )
                    if (showScore) {
                        drawSeriesDashed(
                            values = movingAvg.map { it.scoreAvg },
                            xCenter = xCenter,
                            height = h,
                            color = scoreColor,
                            strokeWidth = 4f,
                        )
                    }

                    // Selection highlight: translucent band + bold primary line + dots
                    selectedIdx?.let { i ->
                        val xc = xCenter(i)
                        // Translucent band the full bin width so the selection is
                        // visible even when there are many narrow bars.
                        val bandW = step.coerceAtLeast(6f)
                        drawRect(
                            color = selectionColor.copy(alpha = 0.18f),
                            topLeft = Offset(xc - bandW / 2f, 0f),
                            size = Size(bandW, h),
                        )
                        drawLine(
                            color = selectionColor,
                            start = Offset(xc, 0f),
                            end = Offset(xc, h),
                            strokeWidth = 3f,
                        )
                        fun dotAt(values: List<Double?>, color: Color) {
                            val v = values.getOrNull(i) ?: return
                            val y = h - (v.toFloat().coerceIn(0f, 1f) * h)
                            drawCircle(color = Color.White, radius = 7f, center = Offset(xc, y))
                            drawCircle(color = color, radius = 5.5f, center = Offset(xc, y))
                        }
                        dotAt(movingAvg.map { it.precision }, precisionColor)
                        dotAt(movingAvg.map { it.recall }, recallColor)
                        if (showScore) dotAt(movingAvg.map { it.scoreAvg }, scoreColor)
                    }

                    // Y-axis labels
                    val paint = android.graphics.Paint().apply {
                        this.color = labelArgb
                        textSize = 22f
                    }
                    drawContext.canvas.nativeCanvas.drawText("1.0", 4f, 18f, paint)
                    drawContext.canvas.nativeCanvas.drawText("0.5", 4f, h / 2f + 6f, paint)
                    drawContext.canvas.nativeCanvas.drawText("0.0", 4f, h - 4f, paint)
                }
            }

            Spacer(Modifier.height(4.dp))
            DayLabelsRow(perDay.map { it.date })

            selectedIdx?.let { idx ->
                val day = perDay.getOrNull(idx) ?: return@let
                val ma = movingAvg.getOrNull(idx)
                Spacer(Modifier.height(6.dp))
                Text(
                    day.date,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val parts = mutableListOf<String>()
                day.precision?.let { parts.add("P: ${it.fmt("%.2f")}") }
                day.recall?.let { parts.add("R: ${it.fmt("%.2f")}") }
                day.f1?.let { parts.add("F1: ${it.fmt("%.2f")}") }
                if (showScore) day.scoreAvg?.let { parts.add("Score: ${it.fmt("%.2f")}") }
                parts.add("TP/FP/FN: ${day.tp}/${day.fp}/${day.fn}")
                Text(
                    parts.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ma != null) {
                    val maParts = mutableListOf<String>()
                    ma.precision?.let { maParts.add("P: ${it.fmt("%.2f")}") }
                    ma.recall?.let { maParts.add("R: ${it.fmt("%.2f")}") }
                    ma.f1?.let { maParts.add("F1: ${it.fmt("%.2f")}") }
                    if (showScore) ma.scoreAvg?.let { maParts.add("Score: ${it.fmt("%.2f")}") }
                    if (maParts.isNotEmpty()) {
                        Text(
                            "7d MA  •  " + maParts.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReIDHelpBlock() {
    val labelStyle = MaterialTheme.typography.labelMedium
    val bodyStyle = MaterialTheme.typography.bodySmall
    val subdued = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "These metrics score auto-detection of the person of interest crossing the gate. " +
                "It's a combination of two things working together: ReID matching the right person, " +
                "and gate-crossing direction detection (away vs back). Either one failing turns an " +
                "auto-detection into a miss.",
            style = bodyStyle,
            color = subdued,
        )
        Text("Precision — how often auto-detections are correct.", style = labelStyle)
        Text(
            "TP / (TP + FP). High precision means when auto fires, it's right; few false alarms " +
                "from wrong ReID matches or wrong direction.",
            style = bodyStyle,
            color = subdued,
        )
        Text("Recall — how many real crossings auto caught.", style = labelStyle)
        Text(
            "TP / (TP + FN). High recall means auto rarely misses; few crossings need manual marking " +
                "because ReID didn't match or direction wasn't determined.",
            style = bodyStyle,
            color = subdued,
        )
        Text("F1 — single-number balance of precision and recall.", style = labelStyle)
        Text(
            "Handy for tracking overall quality over time. Closer to 1.0 is better, 0 is worst.",
            style = bodyStyle,
            color = subdued,
        )
        Text(
            "TP = auto-detection confirmed (kept). FP = auto-detection removed as wrong. " +
                "FN = auto missed it, added manually.",
            style = bodyStyle,
            color = subdued,
        )
        Text("Match score (optional, dashed) — average ReID cosine score.", style = labelStyle)
        Text(
            "Averaged over videos where the person of interest actually crossed (TP + FN). " +
                "Trending up means the gallery is improving; trending down suggests new appearances " +
                "(clothing, lighting) the gallery doesn't yet cover.",
            style = bodyStyle,
            color = subdued,
        )
    }
}

@Composable
private fun ReIDMetricBlock(label: String, value: Double?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.let { (it * 100).fmt("%.1f") + "%" } ?: "–",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double?>,
    xCenter: (Int) -> Float,
    height: Float,
    color: Color,
    strokeWidth: Float,
) {
    // Draw a polyline through defined points, breaking on nulls.
    var prev: Offset? = null
    values.forEachIndexed { i, v ->
        if (v == null) {
            prev = null
            return@forEachIndexed
        }
        val y = height - (v.toFloat().coerceIn(0f, 1f) * height)
        val cur = Offset(xCenter(i), y)
        prev?.let { p ->
            drawLine(color = color, start = p, end = cur, strokeWidth = strokeWidth)
        }
        prev = cur
    }
}

// Dashed variant: builds a single Path so the dash pattern remains continuous
// across segments. Breaks on nulls (starts a fresh sub-path).
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeriesDashed(
    values: List<Double?>,
    xCenter: (Int) -> Float,
    height: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = androidx.compose.ui.graphics.Path()
    var moved = false
    values.forEachIndexed { i, v ->
        if (v == null) {
            moved = false
            return@forEachIndexed
        }
        val y = height - (v.toFloat().coerceIn(0f, 1f) * height)
        val x = xCenter(i)
        if (!moved) {
            path.moveTo(x, y)
            moved = true
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(10f, 6f),
            ),
        ),
    )
}

/**
 * Evenly-spaced day labels across the full width of the chart.
 * Picks ~7 labels max so they don't overlap.
 */
@Composable
private fun DayLabelsRow(days: List<String>) {
    if (days.isEmpty()) return
    val maxLabels = 7
    val step = (days.size.toFloat() / maxLabels).coerceAtLeast(1f)
    val indices = (0 until maxLabels).map { (it * step).toInt().coerceAtMost(days.lastIndex) }.distinct()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val totalWidth = maxWidth
        indices.forEach { idx ->
            val fraction = idx.toFloat() / days.size.coerceAtLeast(1)
            Text(
                days[idx].takeLast(5),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = totalWidth * fraction),
            )
        }
    }
}
