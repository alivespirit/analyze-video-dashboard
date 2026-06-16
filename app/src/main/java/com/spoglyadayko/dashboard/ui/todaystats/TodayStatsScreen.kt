package com.spoglyadayko.dashboard.ui.todaystats

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.spoglyadayko.dashboard.data.api.AwayInterval
import com.spoglyadayko.dashboard.data.api.ChartEntry
import com.spoglyadayko.dashboard.ui.theme.*
import com.spoglyadayko.dashboard.ui.theme.fmt
import com.spoglyadayko.dashboard.ui.theme.toAndroidColor
import org.koin.androidx.compose.koinViewModel

// All possible statuses in display order
private val ALL_STATUSES = listOf(
    "error", "no_motion", "no_significant_motion",
    "no_person", "significant_motion", "gate_crossing",
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
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Status counts — always show all known statuses, tap to exclude
                    item {
                        StatusCountsGrid(
                            total = data.videosTotal,
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

                    // Processing times (stats table + per-video chart, unified)
                    if (data.processingStats.isNotEmpty() || filteredChart.isNotEmpty()) {
                        item {
                            ProcessingTimesCard(
                                stats = data.processingStats,
                                chart = filteredChart,
                            )
                        }
                    }
                }
            }
            state.loading -> {
                com.spoglyadayko.dashboard.ui.components.ShimmerList(rows = 5, rowHeight = 88.dp)
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Lays children out in lines by their NATURAL width (so wrapping matches content, like FlowRow),
// then grows every chip on a line by an equal share of that line's leftover space so each line fills
// the full width. Chip widths stay proportional to content — not equalized.
@Composable
private fun JustifiedFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 6.dp,
    verticalGap: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()

        // Natural single-line width of each child via intrinsics (so each is still measured only once).
        val natural = IntArray(measurables.size) { measurables[it].maxIntrinsicWidth(Int.MAX_VALUE) }

        // Greedy line packing by natural width.
        val lines = mutableListOf<IntRange>()
        var start = 0
        var lineW = 0
        for (i in measurables.indices) {
            val extra = natural[i] + if (i == start) 0 else hGap
            if (i > start && lineW + extra > maxW) {
                lines.add(start until i)
                start = i
                lineW = natural[i]
            } else {
                lineW += extra
            }
        }
        lines.add(start until measurables.size)

        // Grow each child by an equal share of its line's leftover, then measure once at that width.
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        for (line in lines) {
            val count = line.count()
            val naturalSum = line.sumOf { natural[it] }
            val leftover = (maxW - hGap * (count - 1) - naturalSum).coerceAtLeast(0)
            val per = leftover / count
            var rem = leftover % count
            for (i in line) {
                var w = natural[i] + per
                if (rem > 0) { w++; rem-- }
                w = w.coerceAtMost(maxW)
                placeables[i] = measurables[i].measure(Constraints(minWidth = w, maxWidth = w))
            }
        }

        val totalH = lines.withIndex().sumOf { (idx, line) ->
            line.maxOf { placeables[it]!!.height } + if (idx == 0) 0 else vGap
        }

        layout(maxW, totalH) {
            var y = 0
            lines.forEachIndexed { idx, line ->
                if (idx > 0) y += vGap
                val lineH = line.maxOf { placeables[it]!!.height }
                var x = 0
                for (i in line) {
                    val p = placeables[i]!!
                    p.placeRelative(x, y + (lineH - p.height) / 2)
                    x += p.width + hGap
                }
                y += lineH
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCountsGrid(
    total: Int,
    counts: Map<String, Int>,
    excludedStatuses: Set<String>,
    onToggle: (String) -> Unit,
) {
    // Always show all known statuses (even with 0 count), plus any unknown from data
    val unknownStatuses = counts.keys.filter { it !in ALL_STATUSES }.sorted()
    val displayStatuses = ALL_STATUSES + unknownStatuses

    // Theme-aware brand gradient (cyan->blue), matching the wordmark.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brand = if (dark)
        listOf(Color(0xFF65E0FF), Color(0xFF4F86F7))
    else
        listOf(Color(0xFF1591B5), Color(0xFF1E3A8A))

    val bracketColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The bracket only makes sense when chips wrap to multiple rows (portrait); in landscape they're
    // one row, so skip it.
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // Measured number size so each arm can start ABOVE/BELOW the number itself.
    var numberSize by remember { mutableStateOf(IntSize.Zero) }

    Row(
        // Two arms emanate FROM the number: the top one starts just above the number, rises, rounds a
        // corner, then runs right to the top chip; the bottom one starts just below, drops, corners,
        // then runs right to the bottom chip. The number labels the group; the arms reach to the chips.
        modifier = Modifier
            .drawBehind {
                val numW = numberSize.width.toFloat()
                val numH = numberSize.height.toFloat()
                if (numW > 0f && isPortrait) {
                    val sw = 2.dp.toPx()
                    val r = 6.dp.toPx()                   // slightly rounded corners
                    val h = size.height
                    val cy = h * 0.5f
                    val xv = numW * 0.5f                  // vertical stem rises from the number's center
                    val xr = numW + 4.dp.toPx()           // horizontal stops just short of the chips (small gap)
                    val numTop = cy - numH / 2f
                    val numBot = cy + numH / 2f
                    // Corner sits a small gap above/below the number — matched to the line→chip gap.
                    val stemGap = 4.dp.toPx()
                    val cornerTopY = numTop - stemGap
                    val cornerBotY = numBot + stemGap
                    val path = Path().apply {
                        // Top arm: from the number top → up → rounded corner → right to the top chip.
                        moveTo(xv, numTop)
                        lineTo(xv, cornerTopY + r)
                        quadraticTo(xv, cornerTopY, xv + r, cornerTopY)
                        lineTo(xr, cornerTopY)
                        // Bottom arm: from the number bottom → down → rounded corner → right to the bottom chip.
                        moveTo(xv, numBot)
                        lineTo(xv, cornerBotY - r)
                        quadraticTo(xv, cornerBotY, xv + r, cornerBotY)
                        lineTo(xr, cornerBotY)
                    }
                    drawPath(path, bracketColor, style = Stroke(width = sw, cap = StrokeCap.Round))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Count anchor: the total (= sum of these chips) as a big mono gradient number + unit.
        // Count-up to the total, but reserve the FINAL number's width with an invisible placeholder
        // so the anchor stays a constant width while the digits tick up — otherwise the changing
        // digit count would re-wrap the justified chips mid-animation.
        var target by remember { mutableStateOf(0) }
        LaunchedEffect(total) { target = total }
        val animated by animateIntAsState(target, tween(durationMillis = 700), label = "videoCount")
        val numberStyle = MaterialTheme.typography.displaySmall.copy(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            brush = Brush.horizontalGradient(brand),
            // Trim the line-height leading so the text box hugs the digits — the bracket stem starts at
            // the box edge, so this is what actually shrinks the number→line gap.
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
        Column(
            modifier = Modifier.onSizeChanged { numberSize = it },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$total", style = numberStyle, modifier = Modifier.alpha(0f)) // reserves final width
                Text("$animated", style = numberStyle)
            }
            Text(
                "відео",
                style = MaterialTheme.typography.titleMedium.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Pull up toward the number (closes the big number's line-height leading below it).
                modifier = Modifier.offset(y = (-6).dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        JustifiedFlowRow(
            modifier = Modifier.weight(1f),
            horizontalGap = 6.dp,
            verticalGap = 6.dp,
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
                // Onest is wider than Roboto, so tighten the pills (smaller label, zero letter tracking).
                val chipStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = 12.sp)
                Text(
                    "${status.replace("_", " ")}: $count",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    textAlign = TextAlign.Center,
                    style = if (isExcluded) chipStyle.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    ) else chipStyle,
                    color = if (isExcluded) Color.White.copy(alpha = 0.5f)
                        else if (status in listOf("no_person", "no_significant_motion")) Color.Black
                        else Color.White,
                )
            }
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
                val upCount by animateIntAsState(targetValue = counts["up"] ?: 0, label = "gateUp")
                val downCount by animateIntAsState(targetValue = counts["down"] ?: 0, label = "gateDown")
                Text("\u2191 $upCount", fontFamily = JetBrainsMono, color = AwayColor)
                Text("\u2193 $downCount", fontFamily = JetBrainsMono, color = BackColor)
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
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val selColor = if (dark) Color(0xFF65E0FF) else Color(0xFF1591B5)
    // Selected interval index — tap a timeline bar or a list row to highlight the pair in both
    // places (tap again / tap empty to clear). Resets when the day's intervals change.
    var selected by remember(intervals) { mutableStateOf<Int?>(null) }

    // Compute the visible time range:
    //   - left edge: floor(earliest start) to the hour
    //   - right edge: ceil(max(latest end, now)) when viewing today, so the timeline keeps
    //     growing past the last interval; for past days it's ceil(latest end), or 24h if any
    //     interval is still open (shouldn't normally happen on past days).
    val starts = intervals.mapNotNull { parseHhMmToMinutes(it.start) }
    val ends = intervals.mapNotNull { parseHhMmToMinutes(it.end) }
    val hasOpen = intervals.any { it.start != null && it.end == null }

    val earliestStart = starts.minOrNull() ?: 0
    val dayStartMin = (earliestStart / 60) * 60
    val latestEnd = maxOf(
        ends.maxOrNull() ?: dayStartMin,
        nowMinutes ?: if (hasOpen) 24 * 60 else dayStartMin,
    )
    val dayEndMin = (((latestEnd + 59) / 60) * 60).coerceAtLeast(dayStartMin + 60)
    val totalMin = (dayEndMin - dayStartMin).toFloat().coerceAtLeast(60f)

    Column(modifier = Modifier.padding(12.dp)) {
        Text("Away/Back intervals", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        // Timeline bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(intervals, dayStartMin, dayEndMin, totalMin, nowMinutes) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val idx = intervals.indexOfFirst { iv ->
                            val s = parseHhMmToMinutes(iv.start)
                            if (s == null) false else {
                                val e = parseHhMmToMinutes(iv.end) ?: nowMinutes ?: dayEndMin
                                val x1 = (s - dayStartMin) / totalMin * w
                                val x2 = (e - dayStartMin) / totalMin * w
                                offset.x >= x1 - 8f && offset.x <= x2.coerceAtLeast(x1 + 2f) + 8f
                            }
                        }
                        selected = if (idx >= 0 && idx != selected) idx else null
                    }
                },
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

            intervals.forEachIndexed { index, iv ->
                val s = parseHhMmToMinutes(iv.start) ?: return@forEachIndexed
                val isOpen = iv.end == null
                // Ongoing intervals end at "now" when viewing today; otherwise the visible right edge.
                val e = parseHhMmToMinutes(iv.end) ?: nowMinutes ?: dayEndMin
                val x1 = (s - dayStartMin) / totalMin * size.width
                val x2 = (e - dayStartMin) / totalMin * size.width
                val w = (x2 - x1).coerceAtLeast(2f)

                // When one interval is selected, dim the others (matches the bar charts).
                val dimmed = selected != null && index != selected
                if (isOpen) {
                    // Lighter fill + diagonal hatching to indicate "ongoing"
                    drawRect(
                        color = AwayColor.copy(alpha = if (dimmed) 0.18f else 0.4f),
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
                        color = if (dimmed) AwayColor.copy(alpha = 0.3f) else AwayColor,
                        topLeft = Offset(x1, 0f),
                        size = Size(w, size.height),
                    )
                }

                // Selected interval → brand outline.
                if (index == selected) {
                    drawRect(
                        color = selColor,
                        topLeft = Offset(x1, 0f),
                        size = Size(w, size.height),
                        style = Stroke(width = 2.5f),
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

        // Compact list with arrows; tap a row (or its timeline bar) to highlight the pair.
        intervals.forEachIndexed { index, iv ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { selected = if (selected == index) null else index }
                    .background(
                        if (selected == index) selColor.copy(alpha = 0.16f)
                        else Color.Transparent,
                    )
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u2191", color = AwayColor, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(2.dp))
                Text(iv.start ?: "...", style = MaterialTheme.typography.bodySmall.mono())
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
                    Text(iv.end, style = MaterialTheme.typography.bodySmall.mono())
                } else {
                    // Only "ongoing" when viewing today; for past days the end is simply unknown.
                    Text(
                        if (nowMinutes != null) "ongoing" else "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                iv.dur?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "($it)",
                        style = MaterialTheme.typography.bodySmall.mono(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingTimesCard(stats: Map<String, Double>, chart: List<ChartEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Processing times", fontWeight = FontWeight.Medium)

            // Summary table (min/avg/max for MD and Full)
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                    Text("Min", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Text("Avg", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Text("Max", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (stats.containsKey("md_avg")) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Motion Detection", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(stats["md_min"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                        Text(stats["md_avg"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                        Text(stats["md_max"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                    }
                }
                if (stats.containsKey("full_avg")) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Full Processing", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(stats["full_min"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                        Text(stats["full_avg"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                        Text(stats["full_max"]?.let { "${it.fmt("%.1f")}s" } ?: "-", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.mono())
                    }
                }
            }

            // Per-video chart
            var selectedIdx by remember(chart) { mutableStateOf<Int?>(null) }
            if (chart.isNotEmpty()) {
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))

                val maxSeconds = chart.maxOfOrNull { it.seconds } ?: 1.0
                val avgSeconds = chart.map { it.seconds }.average()
                // Compress only when there's a clear outlier (>= 4x average). A power curve (gamma < 1)
                // is gentler than log10 — outliers stay clearly taller while small bars don't vanish.
                // Tune toward 1.0 for more separation (linear), toward 0.5 for more compression.
                val compress = 0.6
                val useSoftScale = avgSeconds > 0 && maxSeconds >= 4.0 * avgSeconds
                val scaleValue: (Double) -> Double = if (useSoftScale) {
                    { v -> Math.pow(v, compress) }
                } else {
                    { v -> v }
                }
                val scaleMax = scaleValue(maxSeconds)

                // Extract hour from each entry's time for hour boundary markers
                val hours = chart.map { it.time?.substringBefore(":")?.toIntOrNull() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "max: ${maxSeconds.fmt("%.0f")}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (useSoftScale) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "soft scale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))

                val outlineArgb = MaterialTheme.colorScheme.outline.toAndroidColor()

                // Bars grow up from the baseline on first show (and when the bar count changes).
                val grow = remember { Animatable(0f) }
                LaunchedEffect(chart.size) {
                    grow.snapTo(0f)
                    grow.animateTo(1f, animationSpec = tween(durationMillis = 600))
                }

                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val selColor = if (dark) Color(0xFF65E0FF) else Color(0xFF1591B5)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                        .pointerInput(chart.size) {
                            detectTapGestures { offset ->
                                val barWidth = size.width.toFloat() / chart.size.coerceAtLeast(1)
                                val idx = (offset.x / barWidth).toInt().coerceIn(0, chart.size - 1)
                                selectedIdx = if (selectedIdx == idx) null else idx
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
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

                        // Draw bars (rounded tops; dim the non-selected when a bar is selected).
                        val anySelected = selectedIdx != null
                        val bw = (barWidth - 1f).coerceAtLeast(1f)
                        val corner = (bw * 0.4f).coerceAtMost(4.dp.toPx())
                        chart.forEachIndexed { i, entry ->
                            val scaledVal = scaleValue(entry.seconds)
                            val barHeight = (scaledVal / scaleMax * size.height).toFloat() * grow.value
                            if (barHeight <= 0f) return@forEachIndexed
                            val left = i * barWidth
                            val a = if (anySelected && selectedIdx != i) 0.28f else 1f
                            val p = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = left, top = size.height - barHeight,
                                        right = left + bw, bottom = size.height,
                                        topLeftCornerRadius = CornerRadius(corner, corner),
                                        topRightCornerRadius = CornerRadius(corner, corner),
                                        bottomRightCornerRadius = CornerRadius.Zero,
                                        bottomLeftCornerRadius = CornerRadius.Zero,
                                    ),
                                )
                            }
                            drawPath(p, statusColor(entry.status).copy(alpha = a))
                        }

                        // Selected bar → brand underline (instead of a white box).
                        selectedIdx?.let { idx ->
                            val uw = 2.5.dp.toPx()
                            val left = idx * barWidth
                            drawLine(
                                selColor,
                                Offset(left, size.height - uw / 2f),
                                Offset(left + bw, size.height - uw / 2f),
                                uw,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }

                // Hour labels below the chart
                Spacer(Modifier.height(2.dp))
                val hourBoundaries = mutableListOf<Pair<Int, Float>>()
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
                        // Sparse data (~1 video/hour) puts hour boundaries on adjacent bars, so their
                        // labels collide. Thin them out: keep a label only if it's at least minSpacing
                        // past the last one we drew. First boundary always shown.
                        val minSpacing = 26.dp
                        var lastShownX: Dp? = null
                        hourBoundaries.forEach { (h, fraction) ->
                            val x = totalWidth * fraction
                            if (lastShownX == null || x - lastShownX!! >= minSpacing) {
                                Text(
                                    "${h}h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(x = x - 8.dp),
                                )
                                lastShownX = x
                            }
                        }
                    }
                }

                // Selected bar details
                selectedIdx?.let { idx ->
                    val entry = chart.getOrNull(idx) ?: return@let
                    Spacer(Modifier.height(6.dp))
                    Text(
                        entry.basename,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val parts = mutableListOf<String>()
                    entry.time?.let { parts.add(it) }
                    entry.status?.let { parts.add(it.replace("_", " ")) }
                    parts.add("${entry.seconds.fmt("%.1f")}s")
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

