package com.spoglyadayko.dashboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.spoglyadayko.dashboard.MainActivity
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

class EventPollService : Service() {

    private val api: DashboardApi by inject()
    private val settings: SettingsStore by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var nextEventNotificationId = EVENT_NOTIFICATION_BASE_ID

    // Cached so a repeat `onStartCommand` (e.g. when the user taps the
    // notification and MainActivity re-starts the service) doesn't reset the
    // foreground notification to the default placeholder until the next poll.
    @Volatile
    private var lastStatusText: String? = null

    companion object {
        const val STATUS_CHANNEL_ID = "spoglyadayko_status"
        const val EVENT_CHANNEL_ID = "spoglyadayko_events"
        const val STATUS_NOTIFICATION_ID = 1
        const val EVENT_NOTIFICATION_BASE_ID = 100
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildStatusNotification(lastStatusText)
        try {
            startForeground(STATUS_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException if a foreground-service time
            // limit is exhausted, or a POST_NOTIFICATIONS restriction. Never let this crash
            // the process (the old dataSync 6h-cap restart loop did exactly that). Give up
            // this start quietly; MainActivity restarts the service when the app is reopened.
            stopSelf()
            return START_NOT_STICKY
        }
        // Only start polling once — ignore duplicate startService calls
        if (pollingJob?.isActive != true) {
            pollingJob = startPolling()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Foreground-service timeout callbacks (Android 14 one-arg / Android 15 two-arg). The
    // specialUse type isn't time-limited, so these shouldn't fire — but if any FGS time limit
    // ever applies, stop cleanly here instead of being force-crashed with
    // ForegroundServiceDidNotStopInTimeException.
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Current home/away status"
            setShowBadge(false)
        }

        val eventChannel = NotificationChannel(
            EVENT_CHANNEL_ID,
            "Away/Back events",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts for away/back events"
            enableVibration(true)
        }

        nm.createNotificationChannel(statusChannel)
        nm.createNotificationChannel(eventChannel)
    }

    private fun buildStatusNotification(statusText: String?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("Споглядайко")
            .setContentText(statusText ?: "спостерігає...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startPolling(): Job {
        val service = this
        return scope.launch {
            // Initial fetch to set status
            var lastTs: String? = null
            try {
                val initial = service.api.getEventsLatest(since = "00:00")
                service.updateStatusNotification(initial.currentStatus, initial.currentStatusSince, initial.nextPrediction)
                lastTs = initial.serverTs
                service.settings.setLastEventTs(initial.serverTs)
            } catch (_: Exception) {}

            while (isActive) {
                val interval = service.settings.pollIntervalSeconds.first()
                delay(interval * 1000L)

                try {
                    val resp = service.api.getEventsLatest(since = lastTs)

                    // Update persistent notification with current status
                    service.updateStatusNotification(resp.currentStatus, resp.currentStatusSince, resp.nextPrediction)

                    // Post individual event notifications with unique IDs
                    resp.events.forEach { event ->
                        val hhmm = event.hhmmss?.substringBeforeLast(":") ?: "?"
                        val text = when (event.type) {
                            "away" -> "Пішла о $hhmm!"
                            "back" -> "Повернулась о $hhmm..."
                            else -> "${event.type} о $hhmm"
                        }
                        service.postEventNotification(text, service.nextEventNotificationId++, event.video)
                    }

                    lastTs = resp.serverTs
                    service.settings.setLastEventTs(resp.serverTs)
                } catch (_: Exception) {
                    // Network error — skip this cycle
                }
            }
        }
    }

    private fun updateStatusNotification(
        status: String?,
        since: String?,
        prediction: com.spoglyadayko.dashboard.data.api.NextPrediction? = null,
    ) {
        val hhmm = since?.substringBeforeLast(":")
        val base = when (status) {
            "home" -> "Вдома з ${hhmm ?: "?"}"
            "away" -> "Десь там з ${hhmm ?: "?"}"
            else -> null
        }
        val predictionText = prediction?.let { p ->
            val pct = (p.confidence * 100).toInt()
            val verb = when (p.kind) {
                "away" -> if (p.imminent) "може піде найближчим часом" else "може піде о ${p.predictedHhmm}"
                "back" -> if (p.imminent) "може повернеться найближчим часом" else "може повернеться о ${p.predictedHhmm}"
                else -> null
            }
            verb?.let { "$it ($pct%)" }
        }
        val statusText = when {
            base != null && predictionText != null -> "$base, $predictionText"
            base != null -> base
            predictionText != null -> predictionText.replaceFirstChar { it.uppercase() }
            else -> null
        }
        // Cache so onStartCommand reuses it instead of resetting to placeholder.
        statusText?.let { lastStatusText = it }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(statusText))
    }

    private fun postEventNotification(text: String, id: Int, videoBasename: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            videoBasename?.let { putExtra("video_basename", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Try to fetch ReID crop for the video. The top-scored crop may be
        // saved as "_reid_best1_m.jpg" (matched person) or plain
        // "_reid_best1.jpg" (no ReID match), so try the matched suffix first
        // and fall back to the unsuffixed name.
        val reidBitmap = videoBasename?.let { basename ->
            val stem = basename.substringBeforeLast(".")
            val candidates = listOf(
                "/api/image/${stem}_reid_best1_m.jpg",
                "/api/image/${stem}_reid_best1.jpg",
            )
            candidates.firstNotNullOfOrNull { path ->
                try {
                    val url = java.net.URL(api.imageUrl(path))
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    } else null
                } catch (_: Exception) { null }
            }
        }

        val builder = Notification.Builder(this, EVENT_CHANNEL_ID)
            .setContentTitle("Споглядайко")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (reidBitmap != null) {
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(reidBitmap)
                    .setSummaryText(text)
            )
            builder.setLargeIcon(reidBitmap)
        }

        val notification = builder.build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(id, notification)
    }
}
