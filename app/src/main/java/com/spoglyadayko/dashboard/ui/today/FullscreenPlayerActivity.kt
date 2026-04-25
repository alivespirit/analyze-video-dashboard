package com.spoglyadayko.dashboard.ui.today

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private const val PLAYER_CONTROLS_TIMEOUT_FAST_MS = 350
private const val PLAYER_CONTROLS_TIMEOUT_NORMAL_MS = 2200
private const val PLAYER_CONTROLS_FAST_PHASE_MS = 1200

class FullscreenPlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_POSITION = "position"

        fun launch(context: Context, url: String, positionMs: Long = 0) {
            context.startActivity(Intent(context, FullscreenPlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_POSITION, positionMs)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Let the PlayerView handle its own insets (keeps controls visible)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val playerView = PlayerView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            controllerAutoShow = true
            controllerShowTimeoutMs = PLAYER_CONTROLS_TIMEOUT_NORMAL_MS
            setControllerHideOnTouch(true)
        }
        setContentView(playerView)

        // Hide system bars after layout is set
        val controller = WindowInsetsControllerCompat(window, playerView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val position = intent.getLongExtra(EXTRA_POSITION, 0)

        player = ExoPlayer.Builder(this).build().also {
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.seekTo(position)
            it.playWhenReady = true
            playerView.player = it

            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        playerView.controllerShowTimeoutMs = PLAYER_CONTROLS_TIMEOUT_FAST_MS
                        playerView.postDelayed({
                            if (playerView.player === it) {
                                playerView.controllerShowTimeoutMs = PLAYER_CONTROLS_TIMEOUT_NORMAL_MS
                            }
                        }, PLAYER_CONTROLS_FAST_PHASE_MS.toLong())
                    } else {
                        playerView.controllerShowTimeoutMs = PLAYER_CONTROLS_TIMEOUT_NORMAL_MS
                    }
                }
            }
            it.addListener(listener)
        }

        playerView.setFullscreenButtonClickListener { finish() }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
