# Споглядайко Dashboard

Android dashboard app for the [analyze-video](https://github.com/alivespirit/analyze-video) surveillance system.

## Features

- **Today** — Per-video summary with status, gate direction, ReID scores, processing time, frames indicator. Tap to view video (with highlight clip), logs, ReID crops, and insignificant/no_person frames. Swipe between tabs.
- **Today's Stats** — Status counts with tap-to-filter, gate crossings, processing time chart (log scale with hour markers), away/back intervals.
- **Overall Stats** — Per-day video counts (selectable bars), processing times per day chart, weekday heatmaps for away/back events (tap cells for details).
- **Monitoring** — Master CPU/RAM/battery, worker status/load/CPU temp/RAM/battery, recent processing ledger. Auto-refreshes every 15 seconds.
- **Notifications** — Foreground service showing current home/away status ("Вдома з 14:05" / "Десь там з 10:15"). Away/back event alerts with ReID crop image preview. Tap notification to open the corresponding video.
- **Date navigation** — Calendar icon in top bar to switch between available log days. Only days with logs are selectable.
- **Light/Dark theme** — Auto-matches device theme, or manually selectable (Auto/Light/Dark) in Settings.
- **ReID gallery management** — Long-press ReID crops to copy to positive or negative gallery.

## Prerequisites

1. Install **Android Studio** (Ladybug 2024.2 or later)
2. Accept Android SDK licenses, install SDK 35
3. On your Android phone:
   - Settings > About > tap "Build number" 7 times to enable Developer Options
   - Developer Options > enable "USB debugging"
4. The master must be running with `ENABLE_LOG_DASHBOARD=true` and the JSON API endpoints available (requires the updated `tools/log_dashboard/app.py`)

## Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install on USB-connected device
./gradlew installDebug

# Or wireless (same WiFi network):
# Phone: Developer Options > Wireless debugging > Pair
adb pair <phone-ip>:<port>
adb connect <phone-ip>:<port>
./gradlew installDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Configuration

In the app, tap the gear icon (Settings):
- **Server URL**: Default `http://192.168.1.33:8192`. Change if your master IP or port differs.
- **Poll interval**: How often the foreground service checks for away/back events (default 30s).
- **Notifications**: Toggle the foreground service on/off.
- **Theme**: Auto (system) / Light / Dark.

## Server-Side API Endpoints

The master needs the updated `tools/log_dashboard/app.py` with these JSON API endpoints:

| Endpoint | Description |
|----------|-------------|
| `GET /api/days` | List of available log days (YYYY-MM-DD) |
| `GET /api/today/videos?day=` | Video summary list with status, ReID, frames indicator |
| `GET /api/today/video/{basename}/logs?day=` | Log entries per video |
| `GET /api/today/video/{basename}/reid-crops` | ReID crop image URLs |
| `GET /api/today/video/{basename}/frames` | Insignificant/no_person frame URLs |
| `GET /api/today/video/{basename}/highlight` | Highlight clip URL if available |
| `GET /api/today/stats?day=` | Today's aggregated stats |
| `GET /api/stats/overall` | Overall stats with heatmaps |
| `GET /api/monitoring` | System monitoring (CPU, RAM, battery, worker health) |
| `GET /api/events/latest?since=` | Away/back events for notifications |
| `POST /api/reid/copy` | Copy ReID crop to positive/negative gallery |
| `GET /api/image/{basename}` | Serve image files (crops, frames) |
| `GET /api/highlight/{basename}` | Serve highlight clips |
| `GET /video/{basename}` | Serve full video files |

The worker also needs the updated `/health` endpoint (in `worker/server.py`) that includes load average, RAM stats, and CPU temperature.

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Ktor Client (HTTP) + kotlinx.serialization (JSON)
- AndroidX Media3 / ExoPlayer (video playback with fullscreen support)
- Coil (image loading with full-resolution zoom)
- Koin (dependency injection)
- Jetpack DataStore (preferences)
- Foreground Service (event polling + notifications with BigPictureStyle)

## Important Notes

- The app uses cleartext HTTP (`android:usesCleartextTraffic="true"`) since the master serves on HTTP within the local network.
- Android 13+ requires runtime notification permission — the app requests it on first launch.
- The foreground service auto-starts on app launch if notifications are enabled.
- Highlight clips are saved to daily directories in TEMP_DIR and kept after Telegram send (controlled by `KEEP_HIGHLIGHTS_CLIPS` env var, default `true`).
- When the worker is enabled but unreachable, the monitoring tab shows it with an "offline" chip instead of hiding it.
