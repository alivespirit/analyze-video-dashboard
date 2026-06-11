# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android companion dashboard app ("Споглядайко") for the [analyze-video](https://github.com/alivespirit/analyze-video) surveillance system. Built with Jetpack Compose and Material 3, it consumes the JSON API served by `tools/log_dashboard/app.py` on the master server to display video analysis results, system monitoring, and event notifications.

## Building and Running

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

**Requirements**: Android Studio Meerkat 2024.3+ (needed to sync AGP 8.10), Android SDK 35, JDK 17, device/emulator with Android 8.0+ (API 26+).

**APK output**: `app/build/outputs/apk/debug/app-debug.apk`

The app requires the master server running with `ENABLE_LOG_DASHBOARD=true` (default port 8192). Server URL is configurable in-app settings (default `http://192.168.1.33:8192`).

There is no automated test suite.

## Architecture

**MVVM** with Jetpack Compose. Each screen has a paired `*Screen.kt` (composable) and `*ViewModel.kt` (StateFlow-based state management).

**Dependency injection**: Koin (configured in `di/AppModule.kt`).

**Key layers**:
- `data/api/DashboardApi.kt` — Ktor HTTP client (OkHttp engine), all API calls
- `data/api/Models.kt` — kotlinx.serialization data classes for all API responses
- `data/preferences/SettingsStore.kt` — Jetpack DataStore for persistent settings
- `service/EventPollService.kt` — foreground service polling `/api/events/latest` for away/back notifications
- `ui/` — Compose screens organized by feature

### Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2026.04.01 | UI framework (compose-ui 1.11) |
| Material 3 | 1.5.0-alpha18 | M3 Expressive (theme, motion, shapes) |
| graphics-shapes | 1.0.1 | Shape morphing for MaterialShapes |
| Haze | 1.6.0 | Frosted-glass backdrop blur (floating nav) |
| Ktor | 3.0.3 | HTTP client |
| Koin | 4.0.1 | Dependency injection |
| Coil | 3.0.4 | Image loading |
| Media3/ExoPlayer | 1.5.1 | Video playback |
| Vico | 2.0.1 | Charts |
| kotlinx.serialization | 1.7.3 | JSON parsing |

### SDK Targets

- `compileSdk` / `targetSdk`: 35
- `minSdk`: 26 (Haze backdrop blur needs API 31+; scrim fallback below)
- Kotlin: 2.1.20, AGP: 8.10.1, Gradle: 8.11.1, Java compatibility: 17

### Design System (M3 Expressive)

Reuse these instead of re-inventing them:

- **Theme** (`ui/theme/Theme.kt`): `SpoglyadaykoTheme` wraps `MaterialExpressiveTheme`; on API 31+ it merges the system (Material You) accent into `primary`/`primaryContainer` only, keeping custom surfaces and the status/ReID/away-back colors in `Color.kt`. `appBackgroundBrush()` is the full-screen gradient (tinted with `tertiary`/`secondary`, never the dynamic primary). The hexagon texture + gradient are painted in `SpoglyadaykoApp.kt`'s Scaffold.
- **Typography** (`ui/theme/Type.kt`): Onest for all roles + JetBrains Mono for numerals (both variable fonts in `res/font/`). Apply `TextStyle.mono()` to any numeric text (times, percentages, counts) for tabular consistency.
- **Motion/components** (`ui/components/`): `Modifier.fadingEdges(top, bottom)` (edge alpha mask) and `Modifier.sheenSweep { progress }` (tab-enter flow) in `FadingEdges.kt`; `ShimmerList` / `rememberShimmerBrush` in `Shimmer.kt` for load placeholders. Lists also use a per-row `graphicsLayer` scroll reveal (scale + ease toward the nearest viewport edge) and a count-aware staggered entrance — see `TodayScreen.kt` / `GateCrossingsScreen.kt`.
- **Floating nav** (`SpoglyadaykoApp.kt`): frosted-glass pill via Haze (`hazeSource` on the pager, `hazeEffect` on the pill); transparency is the `backgroundColor`/`tints` alpha in its `hazeEffect` block.

## Screens and Navigation

Bottom navigation with 4 tabs (Today, Stats, Overall, Monitoring) using HorizontalPager for swipe. Overlay navigation for detail screens.

| Screen | Files | Description |
|--------|-------|-------------|
| Today | `ui/today/TodayScreen.kt`, `TodayViewModel.kt` | Per-video list with status, ReID, processing time. Day picker in top bar. |
| Video Detail | `ui/today/VideoDetailScreen.kt`, `VideoDetailViewModel.kt` | Tabbed: highlight player, logs, ReID crops (long-press to copy to gallery), frames |
| Today Stats | `ui/todaystats/TodayStatsScreen.kt`, `TodayStatsViewModel.kt` | Status counts, gate counts, processing time chart, away/back intervals |
| Overall Stats | `ui/overallstats/OverallStatsScreen.kt`, `OverallStatsViewModel.kt` | Per-day video counts, weekday heatmaps |
| Gate Crossings | `ui/gatecrossings/GateCrossingsScreen.kt`, `GateCrossingsViewModel.kt` | Videos with ReID crops, direction, match scores |
| Monitoring | `ui/monitoring/MonitoringScreen.kt`, `MonitoringViewModel.kt` | Master/worker CPU/RAM/battery/temp, Tesla SoC, processing ledger. Auto-refreshes 15s. |
| Settings | `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt` | Server URL, poll interval, notifications toggle, theme (Auto/Light/Dark) |
| Fullscreen Player | `ui/today/FullscreenPlayerActivity.kt` | Standalone ExoPlayer activity |

## API Endpoints Consumed

All endpoints hit the master server's log dashboard (`/api/*`). Base URL is configurable via settings.

| Endpoint | Purpose |
|----------|---------|
| `GET /api/days` | Available log days |
| `GET /api/today/videos?day=` | Video summaries |
| `GET /api/today/video/{basename}/logs?day=` | Per-video log entries |
| `GET /api/today/video/{basename}/reid-crops` | ReID crop image URLs |
| `GET /api/today/video/{basename}/frames` | Insignificant/no_person frame URLs |
| `GET /api/today/video/{basename}/highlight` | Highlight clip URL |
| `GET /api/today/stats?day=` | Aggregated day stats; today's open away intervals include elapsed `dur` and an `ongoing` flag |
| `GET /api/today/gate-crossings?day=` | Gate crossing videos with crops |
| `GET /api/stats/overall` | Overall stats with heatmaps; weekday/time-of-day heatmaps use cache-extended history beyond log retention, weekday cells include per-bin `away_occurrences` / `back_occurrences` |
| `GET /api/stats/reid` | ReID auto-detection accuracy per day: TP/FP/FN, precision/recall/F1, average match score, 7-day MA. Each per-day entry also returns `events: [{video, hhmmss, kind: TP\|FP\|FN\|FPFN, score, crop_url}]` for the recognition-wall view |
| `GET /api/monitoring` | System monitoring data |
| `GET /api/events/latest?since=` | Away/back events for notifications. May include `next_prediction: {kind, predicted_hhmm, confidence, basis_count, basis_total, imminent}` when the same-weekday pattern has ≥4 samples, ≥30% confidence, and the predicted time is within a 3-hour horizon |
| `POST /api/reid/copy` | Copy ReID crop to pos/neg gallery |
| `GET /api/image/{path}` | Serve crop/frame images |
| `GET /api/highlight/{path}` | Serve highlight clips |
| `GET /video/{basename}` | Serve full video files |

## Network Configuration

- Cleartext HTTP enabled (`usesCleartextTraffic=true`) for local network
- Connection timeout: 5s, read timeout: 15s
- JSON parsing: lenient, ignores unknown keys, coerces input values

## Notification System

`EventPollService` is a foreground service that polls `/api/events/latest` at a configurable interval (default 30s). Two notification channels:
- **Status** (low priority): persistent notification showing current home/away status
- **Events** (high priority, vibration): away/back events with optional ReID crop thumbnails (BigPictureStyle)

Tapping an event notification deep-links to the video detail screen via Intent extras (`video_basename`).

## Language Note

UI text is in Ukrainian. App name: "Споглядайко".
