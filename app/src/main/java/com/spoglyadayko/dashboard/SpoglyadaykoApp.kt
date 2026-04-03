package com.spoglyadayko.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import com.spoglyadayko.dashboard.ui.monitoring.MonitoringScreen
import com.spoglyadayko.dashboard.ui.overallstats.OverallStatsScreen
import com.spoglyadayko.dashboard.ui.settings.SettingsScreen
import com.spoglyadayko.dashboard.ui.theme.SpoglyadaykoTheme
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
    TabDef("Статистика", Icons.Default.BarChart),
    TabDef("Загалом", Icons.Default.Timeline),
    TabDef("Моніторинг", Icons.Default.Monitor),
)

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
        var statusFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
        var selectedDay by remember { mutableStateOf<String?>(null) }
        var showDatePicker by remember { mutableStateOf(false) }
        var availableDays by remember { mutableStateOf<Set<String>>(emptySet()) }
        val api = koinInject<DashboardApi>()
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { tabs.size })

        // Sync bottom bar selection with pager
        LaunchedEffect(pagerState.currentPage) {
            // pager drives bottom bar — no-op here, bottom bar reads pagerState.currentPage
        }

        // Handle deep link from notification
        val activity = LocalContext.current as? MainActivity
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
        val isOverlay = currentRoute?.startsWith("video_detail") == true || currentRoute == "settings"

        Scaffold(
            topBar = {
                val isDetail = currentRoute?.startsWith("video_detail") == true
                val isSettings = currentRoute == "settings"
                val showBackArrow = isDetail || isSettings

                TopAppBar(
                    title = {
                        when {
                            isDetail -> Text("\u0412\u0456\u0434\u0435\u043E")
                            isSettings -> Text("Settings")
                            else -> Image(
                                painter = painterResource(R.drawable.app_title),
                                contentDescription = "\u0421\u043F\u043E\u0433\u043B\u044F\u0434\u0430\u0439\u043A\u043E",
                                modifier = Modifier.height(40.dp),
                                contentScale = ContentScale.FillHeight,
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackArrow) {
                            IconButton(onClick = { navController.popBackStack() }) {
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
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                if (!isOverlay) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val label = if (index == 0 && selectedDay != null) {
                                try {
                                    val d = LocalDate.parse(selectedDay)
                                    "${"%02d".format(d.dayOfMonth)}.${"%02d".format(d.monthValue)}"
                                } catch (_: Exception) { tab.label }
                            } else tab.label
                            NavigationBarItem(
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                // Main swipeable tabs (always composed, hidden behind overlays)
                if (!isOverlay) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        when (page) {
                            0 -> TodayScreen(
                                statusFilter = statusFilter,
                                selectedDay = selectedDay,
                                onVideoClick = { basename ->
                                    navController.navigate("video_detail/$basename")
                                },
                            )
                            1 -> TodayStatsScreen(
                                statusFilter = statusFilter,
                                selectedDay = selectedDay,
                                onStatusFilterChanged = { statusFilter = it },
                            )
                            2 -> OverallStatsScreen()
                            3 -> MonitoringScreen()
                        }
                    }
                }

                // Overlay navigation for detail/settings
                NavHost(
                    navController = navController,
                    startDestination = "empty",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable("empty") {
                        // Transparent placeholder — pager shows through
                    }
                    composable(
                        "video_detail/{basename}",
                        arguments = listOf(navArgument("basename") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val basename = backStackEntry.arguments?.getString("basename") ?: return@composable
                        Surface(modifier = Modifier.fillMaxSize()) {
                            VideoDetailScreen(basename = basename)
                        }
                    }
                    composable("settings") {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
