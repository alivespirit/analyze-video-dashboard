package com.spoglyadayko.dashboard.ui.today

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.spoglyadayko.dashboard.ui.theme.severityColor
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private data class TabDef(val title: String, val key: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoDetailScreen(
    basename: String,
    day: String? = null,
    onBack: () -> Unit = {},
    viewModel: VideoDetailViewModel = koinViewModel { parametersOf(basename, day) },
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Track all PlayerView instances so we can hide them before navigation
    val playerViews = remember { mutableListOf<View>() }
    val hidePlayersAndGoBack = remember(onBack) {
        {
            // Set native views to INVISIBLE immediately — this happens before
            // the Compose navigation animation starts, preventing the hardware
            // surface overlay from showing on top of the transition.
            playerViews.forEach { it.visibility = View.INVISIBLE }
            onBack()
        }
    }

    BackHandler(onBack = hidePlayersAndGoBack)

    val tabs = remember(state.crops, state.frames, state.cropsLoading, state.framesLoading) {
        buildList {
            add(TabDef("Logs", "logs"))
            add(TabDef("Player", "player"))
            if (state.crops.isNotEmpty()) add(TabDef("Crops", "crops"))
            if (state.frames.isNotEmpty()) add(TabDef("Frames", "frames"))
        }
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.copyResult) {
        state.copyResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCopyResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.size - 1),
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 0.dp,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs.getOrNull(page)?.key) {
                    "player" -> PlayerTab(
                        videoUrl = state.videoUrl,
                        highlightUrl = state.highlightUrl,
                        playerViews = playerViews,
                    )
                    "logs" -> LogsTab(state = state)
                    "crops" -> CropsTab(
                        state = state,
                        onCopyToGallery = { url, target -> viewModel.copyToGallery(url, target) },
                    )
                    "frames" -> FramesTab(state = state)
                }
            }
        }
    }
}

@Composable
private fun PlayerTab(
    videoUrl: String,
    highlightUrl: String?,
    playerViews: MutableList<View>,
) {
    val context = LocalContext.current
    var fullscreenPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val highlightPlayer = remember(highlightUrl) {
        highlightUrl?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
            }
        }
    }

    val fullPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            highlightPlayer?.release()
            fullPlayer.release()
        }
    }

    // Fullscreen dialog
    fullscreenPlayer?.let { player ->
        Dialog(
            onDismissRequest = { fullscreenPlayer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            setFullscreenButtonClickListener { fullscreenPlayer = null }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (highlightPlayer != null) {
            item {
                Text(
                    "Highlight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = highlightPlayer
                            setFullscreenButtonClickListener { fullscreenPlayer = highlightPlayer }
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            playerViews.add(this)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            Text(
                "Full video",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = fullPlayer
                        setFullscreenButtonClickListener { fullscreenPlayer = fullPlayer }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        playerViews.add(this)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        }
    }
}

@Composable
private fun LogsTab(state: VideoDetailUiState) {
    if (state.logsLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No log entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(state.logs) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    entry.ts.substringAfterLast(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Surface(
                    color = severityColor(entry.level),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(
                        entry.level.take(4),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (entry.worker) {
                    Text(
                        "[W] ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CropsTab(
    state: VideoDetailUiState,
    onCopyToGallery: (String, String) -> Unit,
) {
    var selectedCrop by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.crops) { cropUrl ->
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(cropUrl)
                        .build(),
                    contentDescription = "ReID crop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(0.6f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                selectedCrop = cropUrl
                                showMenu = true
                            },
                        ),
                )

                DropdownMenu(
                    expanded = showMenu && selectedCrop == cropUrl,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy to positive gallery") },
                        onClick = {
                            showMenu = false
                            onCopyToGallery(cropUrl, "positive")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to negative gallery") },
                        onClick = {
                            showMenu = false
                            onCopyToGallery(cropUrl, "negative")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FramesTab(state: VideoDetailUiState) {
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }

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
                    contentDescription = "Frame fullscreen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset = Offset(
                                        x = offset.x + pan.x,
                                        y = offset.y + pan.y,
                                    )
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

                // Close button
                IconButton(
                    onClick = { fullscreenUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    // Frame list — full width, tap to open fullscreen
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.frames) { frameUrl ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(frameUrl)
                    .build(),
                contentDescription = "Frame",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { fullscreenUrl = frameUrl },
            )
        }
    }
}
