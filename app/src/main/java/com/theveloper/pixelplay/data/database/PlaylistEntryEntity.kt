package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "media_key"],
    foreignKeys = [
        ForeignKey(
            entity = RoomPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "position"]),
        Index(value = ["media_key"])
    ]
)
data class PlaylistEntryEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "media_key") val mediaKey: String,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
