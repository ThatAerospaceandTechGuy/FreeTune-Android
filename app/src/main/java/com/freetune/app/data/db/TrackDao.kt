package com.freetune.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE liked = 1 ORDER BY addedAt DESC")
    fun observeLiked(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL AND localPath != '' ORDER BY addedAt DESC")
    fun observeDownloads(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun find(id: String): TrackEntity?

    @Upsert
    suspend fun upsert(entity: TrackEntity)

    @Query("UPDATE tracks SET localPath = NULL WHERE id = :id")
    suspend fun clearLocalPath(id: String)

    @Query("UPDATE tracks SET lastPlayedAt = 0")
    suspend fun clearRecent()

    @Query("DELETE FROM tracks")
    suspend fun clearAll()
}
