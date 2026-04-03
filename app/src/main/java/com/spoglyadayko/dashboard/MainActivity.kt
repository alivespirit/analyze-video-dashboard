package com.spoglyadayko.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import com.spoglyadayko.dashboard.service.EventPollService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settings: SettingsStore by inject()

    private val _deepLinkVideo = MutableStateFlow<String?>(null)
    val deepLinkVideo: StateFlow<String?> = _deepLinkVideo

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startServiceIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleVideoIntent(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            SpoglyadaykoApp(deepLinkVideo = deepLinkVideo)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVideoIntent(intent)
    }

    private fun handleVideoIntent(intent: Intent?) {
        intent?.getStringExtra("video_basename")?.let {
            _deepLinkVideo.value = it
        }
    }

    fun consumeDeepLink() {
        _deepLinkVideo.value = null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startServiceIfEnabled()
    }

    private fun startServiceIfEnabled() {
        val enabled = runBlocking { settings.notificationsEnabled.first() }
        if (enabled) {
            startForegroundService(Intent(this, EventPollService::class.java))
        }
    }
}
