package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "online_media",
    indices = [Index(value = ["source", "source_id"], unique = true)]
)
data class OnlineMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_key") val mediaKey: String,
    val source: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "last_metadata_update") val lastMetadataUpdate: Long
)
