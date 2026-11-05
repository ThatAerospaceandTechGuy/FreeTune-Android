package com.freetune.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.freetune.app.FreeTuneApp
import com.freetune.app.MainActivity
import com.freetune.app.R

/**
 * Home-screen transport controls. It reads the same PlayerManager state as the
 * notification, so queue edits and playback stay in sync with the in-app player.
 */
class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> update(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = (context.applicationContext as FreeTuneApp).container.playerManager
        when (intent.action) {
            ACTION_TOGGLE -> manager.togglePlayPause()
            ACTION_NEXT -> manager.next()
            ACTION_PREVIOUS -> manager.previous()
            ACTION_OPEN -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAll(context)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.freetune.app.widget.TOGGLE"
        private const val ACTION_NEXT = "com.freetune.app.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.freetune.app.widget.PREVIOUS"
        private const val ACTION_OPEN = "com.freetune.app.widget.OPEN"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PlaybackWidgetProvider::class.java)
            )
            ids.forEach { id -> update(context, manager, id) }
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val state = (context.applicationContext as FreeTuneApp)
                .container
                .playerManager
                .state
                .value
            val views = RemoteViews(context.packageName, R.layout.widget_playback)
            views.setTextViewText(
                R.id.widget_title,
                state.current?.title ?: "FreeTune"
            )
            views.setTextViewText(
                R.id.widget_artist,
                state.current?.artist ?: "Nothing playing"
            )
            views.setImageViewResource(
                R.id.widget_play,
                if (state.isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
            )
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                pending(context, ACTION_PREVIOUS, 10)
            )
            views.setOnClickPendingIntent(
                R.id.widget_play,
                pending(context, ACTION_TOGGLE, 11)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                pending(context, ACTION_NEXT, 12)
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                pending(context, ACTION_OPEN, 13)
            )
            views.setOnClickPendingIntent(
                R.id.widget_open,
                pending(context, ACTION_OPEN, 14)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
