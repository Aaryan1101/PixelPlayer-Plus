package com.theveloper.pixelplay.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngagementDaoTest {
    private lateinit var database: PixelPlayDatabase
    private lateinit var dao: EngagementDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.engagementDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun clippedPlaybackEventsInRange_clipsBothBoundariesAndExcludesOutsideEvents() = runBlocking {
        dao.insertPlaybackEvents(
            listOf(
                event(mediaKey = "local:before", start = 10, end = 90),
                event(mediaKey = "local:left", start = 100, end = 200),
                event(mediaKey = "local:right", start = 250, end = 350),
                event(mediaKey = "local:after", start = 400, end = 500)
            )
        )

        val result = dao.clippedPlaybackEventsInRange(
            startInclusive = 150,
            endInclusive = 300
        )

        assertThat(result.map(PlaybackEventEntity::mediaKey))
            .containsExactly("local:left", "local:right")
            .inOrder()
        assertThat(result.map(PlaybackEventEntity::startedAt))
            .containsExactly(150L, 250L)
            .inOrder()
        assertThat(result.map(PlaybackEventEntity::endedAt))
            .containsExactly(200L, 300L)
            .inOrder()
        assertThat(result.map(PlaybackEventEntity::durationListenedMs))
            .containsExactly(50L, 50L)
            .inOrder()
    }

    @Test
    fun recordPlayback_insertsEventAndUpdatesEngagementAtomically() = runBlocking {
        dao.recordPlayback(
            event(
                mediaKey = "youtube:abc123",
                start = 1_000,
                end = 31_000
            )
        )

        assertThat(dao.playbackEventCount()).isEqualTo(1)
        assertThat(dao.engagement("youtube:abc123")).isEqualTo(
            SongEngagementEntity(
                mediaKey = "youtube:abc123",
                playCount = 1,
                totalListenedMs = 30_000,
                lastPlayedAt = 31_000,
                skipCount = 0
            )
        )
    }

    @Test
    fun importLegacyData_isIdempotentAndKeepsLargestAggregateValues() = runBlocking {
        val importedEvent = event(mediaKey = "local:42", start = 1_000, end = 6_000)
        val initialAggregate = SongEngagementEntity(
            mediaKey = "local:42",
            playCount = 4,
            totalListenedMs = 20_000,
            lastPlayedAt = 6_000,
            skipCount = 1
        )

        dao.importLegacyData(listOf(importedEvent), listOf(initialAggregate))
        dao.importLegacyData(
            events = listOf(importedEvent.copy(id = 0, timestamp = 7_000)),
            engagements = listOf(
                initialAggregate.copy(
                    playCount = 2,
                    totalListenedMs = 10_000,
                    lastPlayedAt = 5_000,
                    skipCount = 0
                )
            )
        )

        assertThat(dao.playbackEventCount()).isEqualTo(1)
        assertThat(dao.engagement("local:42")).isEqualTo(initialAggregate)
    }

    @Test
    fun observeRecentlyPlayedMediaKeys_returnsUniqueKeysByLatestPlayback() = runBlocking {
        dao.insertPlaybackEvents(
            listOf(
                event(mediaKey = "local:one", start = 10, end = 100),
                event(mediaKey = "youtube:two", start = 110, end = 200),
                event(mediaKey = "local:one", start = 210, end = 300),
                event(mediaKey = "remote:three", start = 310, end = 400)
            )
        )

        val result = dao.observeRecentlyPlayedMediaKeys(limit = 3).first()

        assertThat(result)
            .containsExactly("remote:three", "local:one", "youtube:two")
            .inOrder()
    }

    private fun event(
        mediaKey: String,
        start: Long,
        end: Long
    ): PlaybackEventEntity {
        return PlaybackEventEntity(
            mediaKey = mediaKey,
            timestamp = end,
            startedAt = start,
            endedAt = end,
            durationListenedMs = (end - start).coerceAtLeast(0),
            completed = true
        )
    }
}
