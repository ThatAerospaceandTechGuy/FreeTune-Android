package com.freetune.app.data

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.freetune.app.data.db.AppDatabase
import com.freetune.app.data.repo.MusicRepository
import com.freetune.app.data.settings.SettingsStore
import com.freetune.app.playback.PlayerManager
import okhttp3.OkHttpClient

class AppContainer(
    context: Context,
    val http: OkHttpClient,
    val downloadHttp: OkHttpClient
) {

    val context: Context = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        this.context,
        AppDatabase::class.java,
        "freetune.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    val repository = MusicRepository(this.context, database.trackDao(), database.playlistDao())

    val settings = SettingsStore(this.context)

    val playerManager = PlayerManager(this.context, settings)

    val workManager: WorkManager = WorkManager.getInstance(this.context)
}
