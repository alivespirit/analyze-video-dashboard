package com.spoglyadayko.dashboard.ui.gatecrossings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.spoglyadayko.dashboard.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GateCrossingsScreen(
    day: String?,
    viewModel: GateCrossingsViewModel = koinViewModel { parametersOf(day) },
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Fullscreen zoomable dialog
    fullscreenUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .size(coil3.size.Size.ORIGINAL)
                        .build(),
                    contentDescription = "Crop fullscreen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { if (scale <= 1f) fullscreenUrl = null }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset = Offset(offset.x + pan.x, offset.y + pan.y)
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )

                IconButton(
                    onClick = { fullscreenUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                state.items.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.items, key = { it.entry.basename }) { item ->
                            GateCrossingRow(
                                item = item,
                                onCropClick = { fullscreenUrl = it },
                                onCropLongClick = { url ->
                                    menuCropUrl = url
                                    showMenu = true
                                },
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

        // Gallery copy menu as a global dropdown anchored to screen
        DropdownMenu(
            expanded = showMenu && menuCropUrl != null,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy to positive gallery") },
                onClick = {
                    showMenu = false
                    menuCropUrl?.let { viewModel.copyToGallery(it, "positive") }
                },
            )
            DropdownMenuItem(
                text = { Text("Copy to negative gallery") },
                onClick = {
                    showMenu = false
                    menuCropUrl?.let { viewModel.copyToGallery(it, "negative") }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GateCrossingRow(
    item: GateCrossingItem,
    onCropClick: (String) -> Unit,
    onCropLongClick: (String) -> Unit,
) {
    val entry = item.entry

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                )

                // Direction arrow or status badge
                if (entry.direction != null) {
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
                            entry.status.replace("_", " "),
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
                            style = MaterialTheme.typography.labelSmall,
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(cropUrl)
                            .build(),
                        contentDescription = "ReID crop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.6f)
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = { onCropClick(cropUrl) },
                                onLongClick = { onCropLongClick(cropUrl) },
                            ),
                    )
                }
                // Fill remaining slots with spacers if <3 crops
                repeat(3 - item.cropUrls.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
