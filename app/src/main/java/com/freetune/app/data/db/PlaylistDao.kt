package com.freetune.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC, pt.addedAt ASC
        """
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): PlaylistEntity?

    @Insert
    suspend fun create(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(ref: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id AND trackId = :trackId")
    suspend fun removeTrack(id: Long, trackId: String)

    @Query("DELETE FROM playlists")
    suspend fun clearAll()
}
