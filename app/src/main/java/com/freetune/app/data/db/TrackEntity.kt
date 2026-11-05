package com.freetune.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.freetune.app.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val localPath: String?,
    val contentUri: String?,
    val liked: Boolean,
    val lastPlayedAt: Long,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    localPath = localPath,
    contentUri = contentUri,
    liked = liked,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount
)

fun Track.toEntity(addedAt: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    localPath = localPath,
    contentUri = contentUri,
    liked = liked,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    playCount = playCount
)
