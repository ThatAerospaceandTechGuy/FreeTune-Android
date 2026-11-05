package com.freetune.app

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
import androidx.lifecycle.lifecycleScope
import com.freetune.app.data.yt.YouTubeService
import com.freetune.app.playback.OverlayService
import kotlinx.coroutines.launch
import com.freetune.app.ui.FreeTuneRoot
import com.freetune.app.ui.theme.FreeTuneTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FreeTuneTheme {
                FreeTuneRoot()
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Handles freetune://track/<videoId> links from other apps. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "freetune" || data.host != "track") return
        val videoId = data.lastPathSegment?.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launch {
            val track = YouTubeService.getDetails(videoId) ?: return@launch
            (application as FreeTuneApp).container.playerManager.play(track)
        }
    }

    override fun onResume() {
        super.onResume()
        OverlayService.startIfEnabled(this)
    }
}
