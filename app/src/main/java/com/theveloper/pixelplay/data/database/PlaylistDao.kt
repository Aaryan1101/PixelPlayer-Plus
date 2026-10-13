package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaylistWithEntries(
    @Embedded val playlist: RoomPlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
        entity = PlaylistEntryEntity::class
    )
    val entries: List<PlaylistEntryEntity>
)

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun observePlaylists(): Flow<List<PlaylistWithEntries>>

    @Transaction
    @Query("""
        SELECT * FROM playlists
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun searchPlaylists(query: String, limit: Int): List<PlaylistWithEntries>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun playlistCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE id = :playlistId)")
    suspend fun playlistExists(playlistId: String): Boolean

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun maximumPosition(playlistId: String): Int

    @Query("SELECT media_key FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun mediaKeysForPlaylist(playlistId: String): List<String>

    @Query("SELECT playlist_id FROM playlist_entries WHERE media_key = :mediaKey")
    suspend fun playlistIdsContaining(mediaKey: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: RoomPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylists(playlists: List<RoomPlaylistEntity>)

    @Update
    suspend fun updatePlaylistEntity(playlist: RoomPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOnlineMedia(media: List<OnlineMediaEntity>)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun deleteEntriesForPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId AND media_key = :mediaKey")
    suspend fun deleteEntry(playlistId: String, mediaKey: String)

    @Query("DELETE FROM playlist_entries WHERE media_key = :mediaKey")
    suspend fun deleteMediaFromAllPlaylists(mediaKey: String)

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET updated_at = :updatedAt WHERE id IN (:playlistIds)")
    suspend fun touchPlaylists(playlistIds: List<String>, updatedAt: Long)

    @Transaction
    suspend fun importLegacyData(
        playlists: List<RoomPlaylistEntity>,
        entries: List<PlaylistEntryEntity>,
        onlineMedia: List<OnlineMediaEntity>
    ) {
        if (playlistCount() != 0) return
        if (onlineMedia.isNotEmpty()) upsertOnlineMedia(onlineMedia)
        if (playlists.isNotEmpty()) insertPlaylists(playlists)
        if (entries.isNotEmpty()) insertEntries(entries)
    }

    @Transaction
    suspend fun createPlaylistWithEntries(
        playlist: RoomPlaylistEntity,
        entries: List<PlaylistEntryEntity>
    ) {
        insertPlaylist(playlist)
        if (entries.isNotEmpty()) insertEntries(entries)
    }

    @Transaction
    suspend fun replacePlaylist(
        playlist: RoomPlaylistEntity,
        entries: List<PlaylistEntryEntity>
    ) {
        updatePlaylistEntity(playlist)
        deleteEntriesForPlaylist(playlist.id)
        if (entries.isNotEmpty()) insertEntries(entries)
    }

    @Transaction
    suspend fun addMedia(
        playlistId: String,
        mediaKeys: List<String>,
        updatedAt: Long
    ) {
        if (!playlistExists(playlistId)) return
        val existing = mediaKeysForPlaylist(playlistId).toHashSet()
        val newKeys = mediaKeys.distinct().filterNot(existing::contains)
        if (newKeys.isEmpty()) return
        val startPosition = maximumPosition(playlistId) + 1
        insertEntries(
            newKeys.mapIndexed { offset, mediaKey ->
                PlaylistEntryEntity(
                    playlistId = playlistId,
                    mediaKey = mediaKey,
                    position = startPosition + offset,
                    addedAt = updatedAt
                )
            }
        )
        touchPlaylists(listOf(playlistId), updatedAt)
    }

    @Transaction
    suspend fun synchronizeMediaMembership(
        mediaKey: String,
        targetPlaylistIds: List<String>,
        updatedAt: Long
    ): List<String> {
        val targetIds = targetPlaylistIds.distinct().filter { playlistExists(it) }
        val existingIds = playlistIdsContaining(mediaKey)
        val removedIds = existingIds.filterNot(targetIds::contains)

        removedIds.forEach { deleteEntry(it, mediaKey) }
        targetIds.filterNot(existingIds::contains).forEach { playlistId ->
            insertEntries(
                listOf(
                    PlaylistEntryEntity(
                        playlistId = playlistId,
                        mediaKey = mediaKey,
                        position = maximumPosition(playlistId) + 1,
                        addedAt = updatedAt
                    )
                )
            )
        }

        val changedIds = (removedIds + targetIds.filterNot(existingIds::contains)).distinct()
        if (changedIds.isNotEmpty()) touchPlaylists(changedIds, updatedAt)
        return removedIds
    }

    @Transaction
    suspend fun removeMedia(playlistId: String, mediaKey: String, updatedAt: Long) {
        deleteEntry(playlistId, mediaKey)
        touchPlaylists(listOf(playlistId), updatedAt)
    }

    @Transaction
    suspend fun removeMediaFromAllPlaylists(mediaKey: String, updatedAt: Long) {
        val affectedPlaylistIds = playlistIdsContaining(mediaKey)
        if (affectedPlaylistIds.isEmpty()) return
        deleteMediaFromAllPlaylists(mediaKey)
        touchPlaylists(affectedPlaylistIds, updatedAt)
    }

    @Transaction
    suspend fun reorderPlaylist(
        playlistId: String,
        orderedMediaKeys: List<String>,
        updatedAt: Long
    ) {
        if (!playlistExists(playlistId)) return
        deleteEntriesForPlaylist(playlistId)
        insertEntries(
            orderedMediaKeys.distinct().mapIndexed { position, mediaKey ->
                PlaylistEntryEntity(
                    playlistId = playlistId,
                    mediaKey = mediaKey,
                    position = position,
                    addedAt = updatedAt
                )
            }
        )
        touchPlaylists(listOf(playlistId), updatedAt)
    }
}
