package com.freetune.app.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.freetune.app.FreeTuneApp
import com.freetune.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional floating playback bar drawn over other apps.
 * Requires the "display over other apps" permission and is off by default.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var playButton: ImageButton? = null

    private val manager by lazy { (application as FreeTuneApp).container.playerManager }
    private val settingsStore by lazy { (application as FreeTuneApp).container.settings }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)

        scope.launch {
            manager.state.collect { state ->
                val track = state.current
                if (track == null) {
                    detach()
                } else {
                    attach()
                    titleView?.text = track.title
                    artistView?.text = track.artist
                    playButton?.setImageResource(
                        if (state.isPlaying) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !settingsStore.floatingControls.value) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutParams?.let { params ->
            params.width = resources.displayMetrics.widthPixels - dp(24)
            panel?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        detach()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attach() {
        if (panel != null) return
        val width = resources.displayMetrics.widthPixels - dp(24)
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = resources.displayMetrics.heightPixels - dp(190)
        }
        layoutParams = params

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xF21A1A22.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
            elevation = dp(10).toFloat()
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openApp() }
            setPadding(dp(2), dp(2), dp(8), dp(2))
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        artistView = TextView(this).apply {
            setTextColor(0xFFB9B6C8.toInt())
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        info.addView(titleView)
        info.addView(artistView)
        root.addView(info)

        root.addView(button(android.R.drawable.ic_media_previous) { manager.previous() })
        playButton = button(android.R.drawable.ic_media_play) { manager.togglePlayPause() }
        root.addView(playButton)
        root.addView(button(android.R.drawable.ic_media_next) { manager.next() })
        root.addView(button(android.R.drawable.ic_menu_close_clear_cancel) { disableOverlay() })

        runCatching { windowManager.addView(root, params) }
        panel = root
    }

    private fun detach() {
        panel?.let { view -> runCatching { windowManager.removeView(view) } }
        panel = null
        layoutParams = null
    }

    private fun button(icon: Int, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(2)
            }
            setOnClickListener { action() }
        }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun disableOverlay() {
        settingsStore.setFloatingControls(false)
        stopSelf()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, OverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, OverlayService::class.java))
            }
        }

        fun startIfEnabled(context: Context) {
            val app = context.applicationContext as FreeTuneApp
            if (app.container.settings.floatingControls.value &&
                Settings.canDrawOverlays(context)
            ) {
                start(context)
            }
        }

        fun openOverlaySettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
