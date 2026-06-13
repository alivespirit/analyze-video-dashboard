package com.spoglyadayko.dashboard.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.spoglyadayko.dashboard.ui.components.fadingEdges
import com.spoglyadayko.dashboard.ui.components.sheenSweep
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spoglyadayko.dashboard.data.api.VideoSummary
import com.spoglyadayko.dashboard.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    excludedStatuses: Set<String>,
    selectedDay: String?,
    isActive: Boolean = true,
    onVideoClick: (String) -> Unit,
    onClearFilter: () -> Unit = {},
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedDay) {
        viewModel.load(selectedDay)
        listState.scrollToItem(0)
    }

    // Refresh and scroll to top when switching to this tab
    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refresh()
            listState.scrollToItem(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = {
            viewModel.refresh()
            scope.launch { listState.scrollToItem(0) }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            state.data != null -> {
                val data = state.data!!
                val videos = remember(data.videos, excludedStatuses) {
                    data.videos.reversed().let { list ->
                        if (excludedStatuses.isEmpty()) list
                        else list.filter { it.status !in excludedStatuses }
                    }
                }
                // A soft sheen sweeps down the list when this tab becomes active — a "flow" that keeps
                // every row visible (no disappear-then-restagger when swiping in from the main page).
                val sheen = remember { Animatable(1f) }
                LaunchedEffect(isActive) {
                    if (isActive) {
                        sheen.snapTo(0f)
                        sheen.animateTo(1f, animationSpec = tween(durationMillis = 1100))
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .fadingEdges(topFade = 16.dp, bottomFade = 96.dp)
                        .sheenSweep { sheen.value },
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 104.dp),
                ) {
                    if (excludedStatuses.isNotEmpty()) {
                        item {
                            // Slim filter bar: shown/total on the left, a connecting rule, and a clear
                            // (X) on the right (the only place to reset the stats-page filter here).
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "showing ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${videos.size}/${data.videos.size}",
                                    style = MaterialTheme.typography.labelMedium.mono(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(
                                    onClick = onClearFilter,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear filter",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    items(videos, key = { it.basename }) { video ->
                        VideoRow(
                            video = video,
                            modifier = Modifier
                                .graphicsLayer {
                                    // Scroll reveal: rows scale in + ease toward the nearest viewport
                                    // edge — rising IN from the bottom, sliding UP and out at the top.
                                    // Matched by key (not index) to survive the header offset. Alpha is
                                    // owned by fadingEdges/sheenSweep, so it's untouched here.
                                    val li = listState.layoutInfo
                                    val info = li.visibleItemsInfo.firstOrNull { it.key == video.basename }
                                    if (info != null && info.size > 0) {
                                        val sz = info.size.toFloat()
                                        val enterBottom = ((li.viewportEndOffset - info.offset) / sz).coerceIn(0f, 1f)
                                        val enterTop = ((info.offset + info.size - li.viewportStartOffset) / sz).coerceIn(0f, 1f)
                                        val s = 0.94f + 0.06f * minOf(enterBottom, enterTop)
                                        scaleX = s
                                        scaleY = s
                                        translationY = if (enterBottom <= enterTop) (1f - enterBottom) * 16.dp.toPx()
                                            else -(1f - enterTop) * 16.dp.toPx()
                                    }
                                }
                                .animateItem(),
                            onClick = { onVideoClick(video.basename) },
                        )
                    }
                }
            }
            state.loading -> {
                com.spoglyadayko.dashboard.ui.components.ShimmerList(rows = 8, rowHeight = 52.dp)
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time
            Text(
                video.time?.substringBeforeLast(":") ?: "--:--",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ).mono(),
                modifier = Modifier.width(46.dp),
            )

            Spacer(Modifier.width(8.dp))

            // Status badge
            Surface(
                color = statusColor(video.status),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    video.status?.replace("_", " ") ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (video.status in listOf("no_person", "no_significant_motion"))
                        Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Pipeline error marker: detect_motion succeeded but a later stage
            // (Gemini / Telegram) logged an error.
            if (video.pipelineError) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Pipeline error",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // Gate direction
            if (video.gateDirection != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    when (video.gateDirection) {
                        "up" -> "\u2191"
                        "down" -> "\u2193"
                        "both" -> "\u2195"
                        else -> ""
                    },
                    fontSize = 14.sp,
                )
            }

            // ReID
            if (video.reidScore != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = if (video.reidMatched == true) ReidMatched else ReidUnmatched,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "${(video.reidScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.mono(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // ReID negative
            if (video.reidNeg != null) {
                Spacer(Modifier.width(2.dp))
                Surface(
                    color = ReidNeg,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "${(video.reidNeg * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.mono(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // Away/Back
            if (video.awayBack != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = if (video.awayBack == "away") AwayColor else BackColor,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        video.awayBack,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // Frames indicator
            if (video.hasFrames) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Has frames",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }

            // SpeedTrap indicator
            if (video.speedKmh != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = Color(0xFFFF6B00),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color.White,
                        )
                        Text(
                            "${video.speedKmh}",
                            style = MaterialTheme.typography.labelSmall.mono(),
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Processing time
            if (video.processingTimeS != null) {
                Text(
                    "${video.processingTimeS}s",
                    style = MaterialTheme.typography.bodySmall.mono(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Worker / Local indicator
            Spacer(Modifier.width(4.dp))
            if (video.worker) {
                Text(
                    "W",
                    style = MaterialTheme.typography.labelSmall.mono(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                Text(
                    "L",
                    style = MaterialTheme.typography.labelSmall.mono(),
                    color = Color(0xFFFF6B00),
                )
            }
        }
    }
}
