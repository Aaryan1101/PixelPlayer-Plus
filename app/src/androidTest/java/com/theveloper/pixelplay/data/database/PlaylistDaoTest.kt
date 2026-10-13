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
class PlaylistDaoTest {
    private lateinit var database: PixelPlayDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.playlistDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createAndReorderPlaylist_preservesMixedMediaOrderingAndRemovesDuplicates() = runBlocking {
        dao.createPlaylistWithEntries(
            playlist = playlist(id = "mixed"),
            entries = entries(
                playlistId = "mixed",
                mediaKeys = listOf("local:41", "youtube:video-a", "remote:item-b")
            )
        )

        dao.reorderPlaylist(
            playlistId = "mixed",
            orderedMediaKeys = listOf(
                "youtube:video-a",
                "local:41",
                "youtube:video-a",
                "remote:item-b"
            ),
            updatedAt = 2_000
        )

        assertThat(dao.mediaKeysForPlaylist("mixed"))
            .containsExactly("youtube:video-a", "local:41", "remote:item-b")
            .inOrder()
    }

    @Test
    fun addMedia_appendsOnlyNewKeysAndUpdatesPlaylistTimestamp() = runBlocking {
        dao.createPlaylistWithEntries(
            playlist = playlist(id = "append", updatedAt = 1_000),
            entries = entries("append", listOf("local:1"), addedAt = 1_000)
        )

        dao.addMedia(
            playlistId = "append",
            mediaKeys = listOf("local:1", "youtube:new", "youtube:new", "local:2"),
            updatedAt = 3_000
        )

        assertThat(dao.mediaKeysForPlaylist("append"))
            .containsExactly("local:1", "youtube:new", "local:2")
            .inOrder()
        assertThat(dao.observePlaylists().first().single().playlist.updatedAt)
            .isEqualTo(3_000)
    }

    @Test
    fun synchronizeMediaMembership_addsAndRemovesMembershipTransactionally() = runBlocking {
        dao.insertPlaylists(
            listOf(
                playlist(id = "keep"),
                playlist(id = "remove"),
                playlist(id = "add")
            )
        )
        dao.insertEntries(
            entries("keep", listOf("youtube:shared")) +
                entries("remove", listOf("youtube:shared"))
        )

        val removed = dao.synchronizeMediaMembership(
            mediaKey = "youtube:shared",
            targetPlaylistIds = listOf("keep", "add", "missing", "add"),
            updatedAt = 4_000
        )

        assertThat(removed).containsExactly("remove")
        assertThat(dao.playlistIdsContaining("youtube:shared"))
            .containsExactly("keep", "add")
        Unit
    }

    @Test
    fun importLegacyData_doesNotOverwriteAnExistingRoomLibrary() = runBlocking {
        val online = OnlineMediaEntity(
            mediaKey = "youtube:legacy",
            source = "youtube",
            sourceId = "legacy",
            title = "Legacy track",
            artist = "Legacy artist",
            album = null,
            duration = 180_000,
            thumbnailUrl = null,
            lastMetadataUpdate = 1_000
        )

        dao.importLegacyData(
            playlists = listOf(playlist(id = "existing", name = "Existing")),
            entries = entries("existing", listOf("local:1")),
            onlineMedia = listOf(online)
        )
        dao.importLegacyData(
            playlists = listOf(playlist(id = "ignored", name = "Ignored")),
            entries = entries("ignored", listOf("youtube:ignored")),
            onlineMedia = emptyList()
        )

        assertThat(dao.playlistCount()).isEqualTo(1)
        assertThat(dao.playlistExists("existing")).isTrue()
        assertThat(dao.playlistExists("ignored")).isFalse()
        assertThat(dao.mediaKeysForPlaylist("existing")).containsExactly("local:1")
        Unit
    }

    @Test
    fun searchPlaylists_filtersCaseInsensitivelyOrdersAndLimitsInRoom() = runBlocking {
        dao.insertPlaylists(
            listOf(
                playlist(id = "old", name = "Road Mix", updatedAt = 1_000),
                playlist(id = "new", name = "ROAD TRIP", updatedAt = 3_000),
                playlist(id = "middle", name = "Late Road", updatedAt = 2_000),
                playlist(id = "other", name = "Focus", updatedAt = 4_000)
            )
        )

        val results = dao.searchPlaylists(query = "road", limit = 2)

        assertThat(results.map { it.playlist.id })
            .containsExactly("new", "middle")
            .inOrder()
    }

    private fun playlist(
        id: String,
        name: String = id,
        updatedAt: Long = 1_000
    ): RoomPlaylistEntity {
        return RoomPlaylistEntity(
            id = id,
            name = name,
            createdAt = 1_000,
            updatedAt = updatedAt,
            isAiGenerated = false,
            isQueueGenerated = false,
            coverImageUri = null,
            coverColorArgb = null,
            coverIconName = null,
            coverShapeType = null,
            coverShapeDetail1 = null,
            coverShapeDetail2 = null,
            coverShapeDetail3 = null,
            coverShapeDetail4 = null
        )
    }

    private fun entries(
        playlistId: String,
        mediaKeys: List<String>,
        addedAt: Long = 1_000
    ): List<PlaylistEntryEntity> {
        return mediaKeys.mapIndexed { position, mediaKey ->
            PlaylistEntryEntity(
                playlistId = playlistId,
                mediaKey = mediaKey,
                position = position,
                addedAt = addedAt
            )
        }
    }
}
