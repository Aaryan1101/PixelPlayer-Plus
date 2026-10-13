package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["media_key"]),
        Index(value = ["media_key", "timestamp"])
    ]
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_key") val mediaKey: String,
    val timestamp: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "duration_listened_ms") val durationListenedMs: Long,
    val completed: Boolean
)
