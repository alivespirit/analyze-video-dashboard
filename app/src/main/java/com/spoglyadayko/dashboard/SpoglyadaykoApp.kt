package com.spoglyadayko.dashboard

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import com.spoglyadayko.dashboard.ui.monitoring.MonitoringScreen
import com.spoglyadayko.dashboard.ui.overallstats.OverallStatsScreen
import com.spoglyadayko.dashboard.ui.settings.SettingsScreen
import com.spoglyadayko.dashboard.ui.theme.GochiHand
import com.spoglyadayko.dashboard.ui.theme.Onest
import com.spoglyadayko.dashboard.ui.theme.SpoglyadaykoTheme
import com.spoglyadayko.dashboard.ui.theme.mono
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.spoglyadayko.dashboard.ui.today.TodayScreen
import com.spoglyadayko.dashboard.ui.today.VideoDetailScreen
import com.spoglyadayko.dashboard.ui.todaystats.TodayStatsScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TabDef(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("Сьогодні", Icons.Default.Today),
    TabDef("Події", Icons.Default.VideoLibrary),
    TabDef("Загалом", Icons.Default.Timeline),
    TabDef("Моніторинг", Icons.Default.Monitor),
)

// Floating pill navigation. Custom-built (rather than HorizontalFloatingToolbar) for full control:
// a center-aligned Row keeps the tall selected pill and the short icon buttons vertically aligned,
// and Surface(onClick) gives a ripple clipped to the pill shape (no unbounded circle).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
private fun FloatingNavBar(
    currentPage: Int,
    selectedDay: String?,
    hazeState: HazeState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Frosted-glass pill: transparent Surface (keeps the shape + float shadow), with hazeEffect
    // painting the blurred backdrop of the content scrolling beneath it + a translucent tint.
    val glass = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .hazeEffect(state = hazeState) {
                    blurRadius = 24.dp
                    backgroundColor = glass.copy(alpha = 0.40f)
                    tints = listOf(HazeTint(glass.copy(alpha = 0.22f)))
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = currentPage == index
                val label = if (index == 0 && selectedDay != null) {
                    try {
                        val d = LocalDate.parse(selectedDay)
                        "${"%02d".format(d.dayOfMonth)}.${"%02d".format(d.monthValue)}"
                    } catch (_: Exception) { tab.label }
                } else tab.label

                // Every tab is the same Surface (stable identity) so the selection can animate:
                // the background colour fades in/out and the label grows/shrinks via animateContentSize.
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "navItemBg",
                )
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(50),
                    color = bg,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 10.dp)
                            .animateContentSize(),
                    ) {
                        Icon(tab.icon, contentDescription = label, modifier = Modifier.size(20.dp))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpoglyadaykoApp(deepLinkVideo: StateFlow<String?>? = null) {
    val settingsStore = koinInject<SettingsStore>()
    val themeMode by settingsStore.themeMode.collectAsState(initial = SettingsStore.THEME_AUTO)
    val darkTheme = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    SpoglyadaykoTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val settingsStore = koinInject<SettingsStore>()
        val excludedStatuses by settingsStore.excludedStatuses.collectAsState(initial = emptySet())
        var selectedDay by remember { mutableStateOf<String?>(null) }
        var showDatePicker by remember { mutableStateOf(false) }
        var availableDays by remember { mutableStateOf<Set<String>>(emptySet()) }
        val api = koinInject<DashboardApi>()
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { tabs.size })
        val hazeState = remember { HazeState() }

        // Sync bottom bar selection with pager
        LaunchedEffect(pagerState.currentPage) {
            // pager drives bottom bar — no-op here, bottom bar reads pagerState.currentPage
        }

        // Handle deep link from notification
        val activity = LocalActivity.current as? MainActivity
        val videoToOpen = deepLinkVideo?.collectAsState()?.value
        LaunchedEffect(videoToOpen) {
            videoToOpen?.let { basename ->
                navController.navigate("video_detail/$basename") {
                    launchSingleTop = true
                }
                activity?.consumeDeepLink()
            }
        }

        // Fetch available days when date picker is opened
        LaunchedEffect(showDatePicker) {
            if (showDatePicker) {
                try {
                    val resp = api.getDays()
                    availableDays = resp.days.toSet()
                } catch (_: Exception) {}
            }
        }

        // Date picker dialog
        if (showDatePicker) {
            val availableMillis = remember(availableDays) {
                availableDays.mapNotNull { dayStr ->
                    try {
                        LocalDate.parse(dayStr).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    } catch (_: Exception) { null }
                }.toSet()
            }

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDay?.let {
                    LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } ?: System.currentTimeMillis(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        if (availableMillis.isEmpty()) return true
                        return utcTimeMillis in availableMillis
                    }
                },
            )

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            selectedDay = if (picked == LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) null else picked
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        selectedDay = null
                        showDatePicker = false
                    }) { Text("Today") }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Observe current nav route for top bar state
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route
        val isOverlay = currentRoute?.startsWith("video_detail") == true || currentRoute == "settings" || currentRoute == "gate_crossings"

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(com.spoglyadayko.dashboard.ui.theme.appBackgroundBrush())
                // Faint hexagon texture over the gradient — the same clean (un-lifted) texture for both
                // themes, just far fainter on light so the white shows through (avoids the gamma-lift
                // artifacts of a derived light image). Content stays opaque on top.
                .paint(
                    painterResource(R.drawable.logo_background_3),
                    contentScale = ContentScale.Crop,
                    alpha = if (darkTheme) 0.30f else 0.22f,
                    // Light theme: boost saturation so the teal reads through the faint overlay
                    // (saturation doesn't stretch tones, so no banding). Dark theme keeps its colors.
                    colorFilter = if (darkTheme) null
                    else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }),
                ),
            containerColor = Color.Transparent,
            // A transparent container defaults contentColor to unspecified (→ black); set it back so
            // content-area text without an explicit colour stays legible on dark.
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                val isDetail = currentRoute?.startsWith("video_detail") == true
                val isSettings = currentRoute == "settings"
                val isGateCrossings = currentRoute == "gate_crossings"
                val showBackArrow = isDetail || isSettings || isGateCrossings

                TopAppBar(
                    title = {
                        when {
                            isDetail -> Text(
                                currentBackStackEntry?.arguments?.getString("basename") ?: "\u0412\u0456\u0434\u0435\u043E",
                                style = MaterialTheme.typography.titleMedium.mono(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            isSettings -> Text("Settings")
                            isGateCrossings -> Text("\u0425\u0432\u0456\u0440\u0442\u043A\u0430")
                            else -> {
                                // Live wordmark (was a baked PNG): Onest, wide-tracked geometric caps
                                // with the brand cyan\u2192blue gradient + a soft glow halo (Shadow, no
                                // offset). Theme-aware endpoints so it reads on the light background too.
                                val brand = if (darkTheme)
                                    listOf(Color(0xFF65E0FF), Color(0xFF4F86F7))
                                else
                                    listOf(Color(0xFF1591B5), Color(0xFF1E3A8A))
                                val glow = if (darkTheme)
                                    Color(0xFF65E0FF).copy(alpha = 0.55f)
                                else
                                    Color(0xFF1591B5).copy(alpha = 0.30f)
                                // Fill the title slot (left edge \u2192 start of the actions/calendar icon)
                                // and center the wordmark+tagline within it.
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "\u0421\u041F\u041E\u0413\u041B\u042F\u0414\u0410\u0419\u041A\u041E",
                                        style = TextStyle(
                                            brush = Brush.horizontalGradient(brand),
                                            fontFamily = GochiHand,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 38.sp,
                                            letterSpacing = 0.5.sp,
                                            // Bigger glyphs, same vertical footprint: trim the default
                                            // font padding + line-leading so the larger size reclaims
                                            // that space instead of growing the title block height.
                                            lineHeight = 38.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.Both,
                                            ),
                                            shadow = Shadow(
                                                color = glow,
                                                offset = Offset.Zero,
                                                blurRadius = if (darkTheme) 22f else 9f,
                                            ),
                                        ),
                                        maxLines = 1,
                                    )
                                    Text(
                                        "\u0414\u0418\u0412\u0418\u0421\u042C. \u0410\u041D\u0410\u041B\u0406\u0417\u0423\u0419. \u041A\u041E\u041D\u0422\u0420\u041E\u041B\u042E\u0419.",
                                        style = TextStyle(
                                            fontFamily = Onest,
                                            fontWeight = FontWeight.Medium,
                                            // Widened to keep spanning the wordmark's width as it grew.
                                            // letterSpacing is the dial to match the title width exactly.
                                            fontSize = 11.sp,
                                            letterSpacing = 2.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        // Pull the tagline up to tighten the gap under the wordmark.
                                        modifier = Modifier.offset(y = (-3).dp),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (showBackArrow) {
                            val activity = LocalActivity.current as? androidx.activity.ComponentActivity
                            IconButton(onClick = {
                                // For video detail, the BackHandler inside VideoDetailScreen
                                // handles hiding players. Simulate system back to trigger it.
                                if (isDetail) {
                                    activity?.onBackPressedDispatcher?.onBackPressed()
                                } else {
                                    navController.popBackStack()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (!showBackArrow) {
                            IconButton(onClick = { showDatePicker = true }) {
                                if (selectedDay != null) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                                    }
                                } else {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                                }
                            }
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // Transparent so the app's background gradient flows continuously behind the
                        // title instead of the bar reading as a flat opaque block on top of it.
                        containerColor = Color.Transparent,
                    ),
                )
            },
            // No bottomBar — the nav is a floating pill overlaid on the content (see FloatingNavBar
            // at the bottom of the content Box), so it visually hovers over the screen.
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                // Main swipeable tabs (always composed, hidden behind overlays). Fade the pager in/out
                // (instead of snapping alpha) so it cross-fades with the overlay's NavHost transition
                // below — same 220ms tween on both sides keeps the back-transition coordinated.
                val pagerAlpha by animateFloatAsState(
                    targetValue = if (isOverlay) 0f else 1f,
                    animationSpec = tween(durationMillis = 220),
                    label = "pagerOverlayAlpha",
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().alpha(pagerAlpha).hazeSource(hazeState),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isOverlay,
                ) { page ->
                        when (page) {
                            0 -> TodayStatsScreen(
                                excludedStatuses = excludedStatuses,
                                selectedDay = selectedDay,
                                onExcludedChanged = { scope.launch { settingsStore.setExcludedStatuses(it) } },
                                onGateCrossingsClick = { navController.navigate("gate_crossings") },
                            )
                            1 -> TodayScreen(
                                excludedStatuses = excludedStatuses,
                                selectedDay = selectedDay,
                                isActive = pagerState.currentPage == 1,
                                onVideoClick = { basename ->
                                    navController.navigate("video_detail/$basename")
                                },
                                onClearFilter = { scope.launch { settingsStore.setExcludedStatuses(emptySet()) } },
                            )
                            2 -> OverallStatsScreen(
                                onVideoClick = { basename, day ->
                                    // Pass day through the route so we don't have to mutate
                                    // the global day picker just to open one detail screen.
                                    navController.navigate("video_detail/$basename?day=$day")
                                },
                            )
                            3 -> MonitoringScreen()
                        }
                    }

                // Overlay navigation for detail/settings
                NavHost(
                    navController = navController,
                    startDestination = "empty",
                    modifier = Modifier.fillMaxSize(),
                    // Match the pager's 220ms fade so overlay and main page cross-fade in lockstep
                    // (was the default ~700ms fade, which desynced from the instant pager reveal).
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(220)) },
                ) {
                    composable("empty") {
                        // Transparent placeholder — pager shows through
                    }
                    composable(
                        "video_detail/{basename}?day={day}",
                        arguments = listOf(
                            navArgument("basename") { type = NavType.StringType },
                            navArgument("day") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { backStackEntry ->
                        val basename = backStackEntry.arguments?.getString("basename") ?: return@composable
                        // Day from the route wins; falls back to the global picker for
                        // callers that don't pass one (e.g. Today / Gate crossings tabs).
                        val day = backStackEntry.arguments?.getString("day") ?: selectedDay
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                            VideoDetailScreen(
                                basename = basename,
                                day = day,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("settings") {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                            SettingsScreen()
                        }
                    }
                    composable("gate_crossings") {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                            com.spoglyadayko.dashboard.ui.gatecrossings.GateCrossingsScreen(
                                day = selectedDay,
                                onVideoClick = { basename ->
                                    navController.navigate("video_detail/$basename")
                                },
                            )
                        }
                    }
                }

                // Floating nav pill — drawn on top of the pager so it hovers over the content.
                if (!isOverlay) {
                    FloatingNavBar(
                        currentPage = pagerState.currentPage,
                        selectedDay = selectedDay,
                        hazeState = hazeState,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
