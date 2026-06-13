package com.spoglyadayko.dashboard.ui.gatecrossings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.spoglyadayko.dashboard.ui.components.FullscreenImageDialog
import com.spoglyadayko.dashboard.ui.components.fadingEdges
import com.spoglyadayko.dashboard.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GateCrossingsScreen(
    day: String?,
    onVideoClick: ((String) -> Unit)? = null,
    viewModel: GateCrossingsViewModel = koinViewModel { parametersOf(day) },
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var pendingScrollToTopAfterRefresh by remember { mutableStateOf(false) }

    // Staggered entrance: one driver ramps 0→target on load; each row reveals over its own slice
    // (fade + slight rise). The page is opened directly (not scroll-reached), so it always plays in
    // full. Target is count-aware so the LAST row fully reveals too (revealSlice past its delay).
    val stagger = 0.06f
    val revealSlice = 0.35f
    val entranceTarget = (state.items.size - 1).coerceAtLeast(0) * stagger + revealSlice
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty()) {
            entrance.snapTo(0f)
            // Scale duration with the ramp so per-row pacing is constant regardless of count.
            entrance.animateTo(
                entranceTarget,
                animationSpec = tween(durationMillis = (entranceTarget * 550).toInt().coerceIn(400, 1200)),
            )
        }
    }

    // Fullscreen image state
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }
    // Long-press menu state
    var menuCropUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.copyResult) {
        state.copyResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCopyResult()
        }
    }

    // Scroll after refresh has finished so newly inserted items are laid out.
    LaunchedEffect(state.loading, state.items.size, pendingScrollToTopAfterRefresh) {
        if (pendingScrollToTopAfterRefresh && !state.loading && state.items.isNotEmpty()) {
            listState.scrollToItem(0)
            pendingScrollToTopAfterRefresh = false
        }
    }

    // Fullscreen zoomable dialog
    fullscreenUrl?.let { url ->
        FullscreenImageDialog(
            imageUrl = url,
            onDismiss = { fullscreenUrl = null },
            contentDescription = "Crop fullscreen",
        )
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                pendingScrollToTopAfterRefresh = true
                viewModel.load()
            },
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
                state.items.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(topFade = 16.dp, bottomFade = 16.dp),
                        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(state.items, key = { _, it -> it.entry.basename }) { index, item ->
                            // Reveal window for this row: opens at index*stagger, full revealSlice later.
                            val p = ((entrance.value - index * stagger) / revealSlice).coerceIn(0f, 1f)
                            GateCrossingRow(
                                item = item,
                                modifier = Modifier
                                    .graphicsLayer {
                                        // Entrance stagger (on load): fade + rise. alpha is also used by
                                        // fadingEdges' mask, so after load (p=1) only the edge fade remains.
                                        alpha = p
                                        // Scroll reveal: rows scale in + ease toward the nearest viewport
                                        // edge — rising IN from the bottom, sliding UP and out at the top.
                                        val li = listState.layoutInfo
                                        val info = li.visibleItemsInfo.firstOrNull { it.index == index }
                                        val edgeShift = if (info != null && info.size > 0) {
                                            val sz = info.size.toFloat()
                                            val enterBottom = ((li.viewportEndOffset - info.offset) / sz).coerceIn(0f, 1f)
                                            val enterTop = ((info.offset + info.size - li.viewportStartOffset) / sz).coerceIn(0f, 1f)
                                            val s = 0.94f + 0.06f * minOf(enterBottom, enterTop)
                                            scaleX = s
                                            scaleY = s
                                            if (enterBottom <= enterTop) (1f - enterBottom) * 16.dp.toPx()
                                            else -(1f - enterTop) * 16.dp.toPx()
                                        } else 0f
                                        translationY = (1f - p) * 24.dp.toPx() + edgeShift
                                    }
                                    .animateItem(),
                                showMenu = showMenu,
                                menuCropUrl = menuCropUrl,
                                onCropClick = { fullscreenUrl = it },
                                onCropLongClick = { url ->
                                    menuCropUrl = url
                                    showMenu = true
                                },
                                onCopyToGallery = { url, target -> viewModel.copyToGallery(url, target) },
                                onDismissMenu = { showMenu = false },
                                onRowClick = onVideoClick?.let { click -> { click(item.entry.basename) } },
                            )
                        }
                    }
                }
                !state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No ReID crops", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GateCrossingRow(
    item: GateCrossingItem,
    modifier: Modifier = Modifier,
    showMenu: Boolean,
    menuCropUrl: String?,
    onCropClick: (String) -> Unit,
    onCropLongClick: (String) -> Unit,
    onCopyToGallery: (String, String) -> Unit,
    onDismissMenu: () -> Unit,
    onRowClick: (() -> Unit)? = null,
) {
    val entry = item.entry

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        onClick = { onRowClick?.invoke() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column: time + direction/status + ReID
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Time (HH:MM)
                Text(
                    entry.time?.substringBeforeLast(":") ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium.mono(),
                    fontSize = 13.sp,
                )

                // Direction arrow(s) with counts, or status badge
                val up = entry.personsUp ?: 0
                val down = entry.personsDown ?: 0
                val hasCounts = entry.personsUp != null || entry.personsDown != null
                val isSimpleSingle = hasCounts && (up + down == 1)

                if (hasCounts && !isSimpleSingle) {
                    // Show counts + arrows when more than one person crossed
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (up > 0) {
                            Text(
                                "$up\u2191",
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono,
                                color = AwayColor,
                            )
                        }
                        if (down > 0) {
                            Text(
                                "$down\u2193",
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono,
                                color = BackColor,
                            )
                        }
                    }
                } else if (entry.direction != null) {
                    Text(
                        when (entry.direction) {
                            "up" -> "\u2191"
                            "down" -> "\u2193"
                            "both" -> "\u2195"
                            else -> ""
                        },
                        fontSize = 18.sp,
                        color = if (entry.direction == "down") BackColor else AwayColor,
                    )
                } else if (entry.status != null) {
                    Surface(
                        color = statusColor(entry.status),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            when (entry.status) {
                                "significant_motion" -> "motion"
                                "no_significant_motion" -> "no motion"
                                else -> entry.status.replace("_", " ")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1,
                        )
                    }
                }

                // ReID match indicator
                if (entry.reidScore != null) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        color = if (entry.reidMatched == true) ReidMatched else ReidUnmatched,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            "${(entry.reidScore * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.mono(),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // ReID negative score
                if (entry.reidNeg != null) {
                    Spacer(Modifier.height(1.dp))
                    Surface(
                        color = ReidNeg,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            "${(entry.reidNeg * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.mono(),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // Away/Back
                if (entry.awayBack != null) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        color = if (entry.awayBack == "away") AwayColor else BackColor,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            entry.awayBack,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Crop thumbnails
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                item.cropUrls.forEach { cropUrl ->
                    val isMatched = cropUrl.substringAfterLast('/').lowercase().endsWith("_m.jpg")
                    Box(modifier = Modifier.weight(1f)) {
                        val imageModifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.6f)
                            .clip(RoundedCornerShape(6.dp))
                            .let {
                                if (isMatched) it.border(
                                    width = 2.dp,
                                    color = ReidMatched,
                                    shape = RoundedCornerShape(6.dp),
                                ) else it
                            }
                            .combinedClickable(
                                onClick = { onCropClick(cropUrl) },
                                onLongClick = { onCropLongClick(cropUrl) },
                            )
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(cropUrl)
                                .build(),
                            contentDescription = "ReID crop",
                            contentScale = ContentScale.Crop,
                            modifier = imageModifier,
                        )
                        DropdownMenu(
                            expanded = showMenu && menuCropUrl == cropUrl,
                            onDismissRequest = onDismissMenu,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy to positive gallery") },
                                onClick = {
                                    onDismissMenu()
                                    onCopyToGallery(cropUrl, "positive")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy to negative gallery") },
                                onClick = {
                                    onDismissMenu()
                                    onCopyToGallery(cropUrl, "negative")
                                },
                            )
                        }
                    }
                }
                // Fill remaining slots with spacers if <3 crops
                repeat(3 - item.cropUrls.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
