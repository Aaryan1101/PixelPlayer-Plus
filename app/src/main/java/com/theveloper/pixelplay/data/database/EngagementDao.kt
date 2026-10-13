package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementDao {
    @Insert
    suspend fun insertPlaybackEvent(event: PlaybackEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<SongEngagementEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaybackEvents(events: List<PlaybackEventEntity>)

    @Query(
        """
        INSERT INTO song_engagement(
            media_key, play_count, total_listened_ms, last_played_at, skip_count
        ) VALUES (
            :mediaKey, 1, :durationListenedMs, :playedAt, 0
        )
        ON CONFLICT(media_key) DO UPDATE SET
            play_count = play_count + 1,
            total_listened_ms = total_listened_ms + :durationListenedMs,
            last_played_at = MAX(last_played_at, :playedAt)
        """
    )
    suspend fun incrementEngagement(
        mediaKey: String,
        durationListenedMs: Long,
        playedAt: Long
    )

    @Query(
        """
        INSERT INTO song_engagement(
            media_key, play_count, total_listened_ms, last_played_at, skip_count
        ) VALUES (
            :mediaKey, 0, 0, :skippedAt, 1
        )
        ON CONFLICT(media_key) DO UPDATE SET
            skip_count = skip_count + 1,
            last_played_at = MAX(last_played_at, :skippedAt)
        """
    )
    suspend fun incrementSkip(mediaKey: String, skippedAt: Long)

    @Query(
        """
        INSERT INTO song_engagement(
            media_key, play_count, total_listened_ms, last_played_at, skip_count
        ) VALUES (
            :mediaKey, :playCount, :totalListenedMs, :lastPlayedAt, :skipCount
        )
        ON CONFLICT(media_key) DO UPDATE SET
            play_count = MAX(play_count, :playCount),
            total_listened_ms = MAX(total_listened_ms, :totalListenedMs),
            last_played_at = MAX(last_played_at, :lastPlayedAt),
            skip_count = MAX(skip_count, :skipCount)
        """
    )
    suspend fun mergeLegacyEngagement(
        mediaKey: String,
        playCount: Int,
        totalListenedMs: Long,
        lastPlayedAt: Long,
        skipCount: Int
    )

    @Query("SELECT * FROM playback_events ORDER BY timestamp ASC")
    suspend fun allPlaybackEvents(): List<PlaybackEventEntity>

    @Query(
        """
        SELECT media_key
        FROM playback_events
        GROUP BY media_key
        ORDER BY MAX(timestamp) DESC, media_key ASC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayedMediaKeys(limit: Int): Flow<List<String>>

    @Query(
        """
        SELECT
            id,
            media_key,
            MIN(ended_at, :endInclusive) AS timestamp,
            MAX(started_at, :startInclusive) AS started_at,
            MIN(ended_at, :endInclusive) AS ended_at,
            CASE
                WHEN MIN(ended_at, :endInclusive) > MAX(started_at, :startInclusive)
                    THEN MIN(ended_at, :endInclusive) - MAX(started_at, :startInclusive)
                WHEN duration_listened_ms > 0 THEN duration_listened_ms
                ELSE 0
            END AS duration_listened_ms,
            completed
        FROM playback_events
        WHERE timestamp >= :startInclusive AND started_at <= :endInclusive
        ORDER BY timestamp ASC
        """
    )
    suspend fun clippedPlaybackEventsInRange(
        startInclusive: Long,
        endInclusive: Long
    ): List<PlaybackEventEntity>

    @Query("SELECT * FROM song_engagement")
    suspend fun allEngagements(): List<SongEngagementEntity>

    @Query("SELECT * FROM song_engagement WHERE media_key = :mediaKey LIMIT 1")
    suspend fun engagement(mediaKey: String): SongEngagementEntity?

    @Query("SELECT COUNT(*) FROM playback_events")
    suspend fun playbackEventCount(): Int

    @Query("SELECT COUNT(*) FROM song_engagement")
    suspend fun engagementCount(): Int

    @Query("DELETE FROM playback_events WHERE timestamp < :cutoff")
    suspend fun prunePlaybackEvents(cutoff: Long): Int

    @Transaction
    suspend fun recordPlayback(
        event: PlaybackEventEntity,
        updateEngagement: Boolean = true
    ) {
        insertPlaybackEvent(event)
        if (updateEngagement) {
            incrementEngagement(event.mediaKey, event.durationListenedMs, event.timestamp)
        }
    }

    @Transaction
    suspend fun importLegacyData(
        events: List<PlaybackEventEntity>,
        engagements: List<SongEngagementEntity>
    ) {
        if (playbackEventCount() == 0 && events.isNotEmpty()) {
            insertPlaybackEvents(events)
        }
        engagements.forEach { engagement ->
            mergeLegacyEngagement(
                mediaKey = engagement.mediaKey,
                playCount = engagement.playCount,
                totalListenedMs = engagement.totalListenedMs,
                lastPlayedAt = engagement.lastPlayedAt,
                skipCount = engagement.skipCount
            )
        }
    }
}
