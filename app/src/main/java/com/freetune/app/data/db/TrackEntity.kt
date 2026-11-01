package com.freetune.app.data.db

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
    val liked: Boolean,
    val lastPlayedAt: Long,
    val addedAt: Long
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    localPath = localPath,
    liked = liked,
    lastPlayedAt = lastPlayedAt
)

fun Track.toEntity(addedAt: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    localPath = localPath,
    liked = liked,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt
)
