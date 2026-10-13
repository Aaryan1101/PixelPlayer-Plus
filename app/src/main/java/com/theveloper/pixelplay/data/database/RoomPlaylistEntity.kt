package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class RoomPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_ai_generated") val isAiGenerated: Boolean,
    @ColumnInfo(name = "is_queue_generated") val isQueueGenerated: Boolean,
    @ColumnInfo(name = "cover_image_uri") val coverImageUri: String?,
    @ColumnInfo(name = "cover_color_argb") val coverColorArgb: Int?,
    @ColumnInfo(name = "cover_icon_name") val coverIconName: String?,
    @ColumnInfo(name = "cover_shape_type") val coverShapeType: String?,
    @ColumnInfo(name = "cover_shape_detail_1") val coverShapeDetail1: Float?,
    @ColumnInfo(name = "cover_shape_detail_2") val coverShapeDetail2: Float?,
    @ColumnInfo(name = "cover_shape_detail_3") val coverShapeDetail3: Float?,
    @ColumnInfo(name = "cover_shape_detail_4") val coverShapeDetail4: Float?
)
