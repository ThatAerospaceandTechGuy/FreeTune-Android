package com.freetune.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.freetune.app.data.AppContainer
import com.freetune.app.data.yt.AndroidDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class FreeTuneApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // Downloads can take many minutes on slow connections: no whole-call timeout.
        val downloadHttp = http.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        NewPipe.init(
            AndroidDownloader(http),
            Localization.fromLocale(Locale.getDefault())
        )

        container = AppContainer(this, http, downloadHttp)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_PLAYBACK = "playback"
    }
}
