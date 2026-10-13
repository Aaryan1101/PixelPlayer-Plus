package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_engagement")
data class SongEngagementEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_key") val mediaKey: String,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "total_listened_ms") val totalListenedMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    @ColumnInfo(name = "skip_count") val skipCount: Int
)
