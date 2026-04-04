package com.spoglyadayko.dashboard.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spoglyadayko.dashboard.data.api.VideoSummary
import com.spoglyadayko.dashboard.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    statusFilter: Set<String>,
    selectedDay: String?,
    onVideoClick: (String) -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDay) {
        viewModel.load(selectedDay)
    }

    // Scroll to top when new data arrives (after refresh or day change)
    LaunchedEffect(state.data) {
        if (state.data != null) {
            listState.scrollToItem(0)
        }
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
                val videos = data.videos.reversed().let { list ->
                    if (statusFilter.isEmpty()) list
                    else list.filter { it.status in statusFilter }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (statusFilter.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    "filtered: ${videos.size}/${data.videos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    items(videos, key = { it.basename }) { video ->
                        VideoRow(video = video, onClick = { onVideoClick(video.basename) })
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
private fun VideoRow(video: VideoSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
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
                ),
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
                        style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.labelSmall,
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
                    Text(
                        "${video.speedKmh}km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Processing time
            if (video.processingTimeS != null) {
                Text(
                    "${video.processingTimeS}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Worker indicator
            if (video.worker) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "W",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
