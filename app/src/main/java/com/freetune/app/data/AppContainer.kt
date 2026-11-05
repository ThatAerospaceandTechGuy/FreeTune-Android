package com.freetune.app.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.freetune.app.data.db.AppDatabase
import com.freetune.app.data.lyrics.LyricsStore
import com.freetune.app.data.repo.MusicRepository
import com.freetune.app.data.session.SessionStore
import com.freetune.app.data.settings.SettingsStore
import com.freetune.app.playback.PlayerManager
import com.freetune.app.widget.PlaybackWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(
    context: Context,
    val http: OkHttpClient,
    val downloadHttp: OkHttpClient
) {

    val context: Context = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase = Room.databaseBuilder(
        this.context,
        AppDatabase::class.java,
        "freetune.db"
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )
        .fallbackToDestructiveMigration()
        .build()

    val repository = MusicRepository(
        this.context,
        database,
        database.trackDao(),
        database.playlistDao()
    )

    val settings = SettingsStore(this.context)

    val lyrics = LyricsStore(this.context, http)

    val playerManager = PlayerManager(this.context, settings, SessionStore(this.context))

    val workManager: WorkManager = WorkManager.getInstance(this.context)

    init {
        // Record every track that starts, including autoplay picks and local files.
        playerManager.onTrackStarted = { track ->
            // Private listening keeps the song out of Recent (and out of the daily mix).
            if (!settings.incognito.value) {
                appScope.launch { repository.markPlayed(track) }
            }
        }

        // Keep the home-screen widget in step with playback and queue changes.
        appScope.launch {
            playerManager.state
                .map { state ->
                    listOf(
                        state.current?.id,
                        state.current?.title,
                        state.current?.artist,
                        state.isPlaying
                    )
                }
                .distinctUntilChanged()
                .collect { PlaybackWidgetProvider.updateAll(this@AppContainer.context) }
        }
    }
}
