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
class MusicSearchDaoTest {
    private lateinit var database: PixelPlayDatabase
    private lateinit var dao: MusicDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PixelPlayDatabase::class.java)
            .addCallback(PixelPlayDatabase.createSearchIndexCallback())
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun searchSongs_appliesDatabaseLimitInRelevanceOrder() = runBlocking {
        seedLibrary(
            artists = listOf(artist(1, "Artist")),
            albums = listOf(album(10, "Album", artistId = 1)),
            songs = listOf(
                song(1, "Result Exact", artistId = 1, albumId = 10),
                song(2, "Result Second", artistId = 1, albumId = 10),
                song(3, "Result Third", artistId = 1, albumId = 10)
            ),
            crossRefs = listOf(
                crossRef(songId = 1, artistId = 1),
                crossRef(songId = 2, artistId = 1),
                crossRef(songId = 3, artistId = 1)
            )
        )

        val results = dao.searchSongs(
            query = "Result",
            allowedParentDirs = emptyList(),
            applyDirectoryFilter = false,
            limit = 2
        ).first()

        assertThat(results.map { it.title })
            .containsExactly("Result Exact", "Result Second")
            .inOrder()
    }

    @Test
    fun songRelationshipBatches_returnOnlyRequestedSongMetadata() = runBlocking {
        seedLibrary(
            artists = listOf(
                artist(1, "Primary"),
                artist(2, "Featured"),
                artist(3, "Other")
            ),
            albums = listOf(album(10, "Album", artistId = 1)),
            songs = listOf(
                song(1, "Requested", artistId = 1, albumId = 10),
                song(2, "Other song", artistId = 3, albumId = 10)
            ),
            crossRefs = listOf(
                crossRef(songId = 1, artistId = 1),
                crossRef(songId = 1, artistId = 2, isPrimary = false),
                crossRef(songId = 2, artistId = 3)
            )
        )

        assertThat(dao.getArtistsForSongs(listOf(1)).first().map { it.id })
            .containsExactly(1L, 2L)
        assertThat(dao.getCrossRefsForSongs(listOf(1)).first().map { it.songId })
            .containsExactly(1L, 1L)
        assertThat(dao.getCrossRefsForArtists(listOf(2)).first().map { it.songId })
            .containsExactly(1L)
        Unit
    }

    @Test
    fun matchedAccessBatches_includeAlbumPrimaryAndFeaturedArtistSongs() = runBlocking {
        seedLibrary(
            artists = listOf(
                artist(1, "Primary"),
                artist(2, "Featured"),
                artist(3, "Other")
            ),
            albums = listOf(
                album(10, "Requested album", artistId = 1),
                album(20, "Other album", artistId = 3)
            ),
            songs = listOf(
                song(1, "Primary song", artistId = 1, albumId = 10),
                song(2, "Featured song", artistId = 3, albumId = 10),
                song(3, "Other song", artistId = 3, albumId = 20)
            ),
            crossRefs = listOf(
                crossRef(songId = 1, artistId = 1),
                crossRef(songId = 2, artistId = 3),
                crossRef(songId = 2, artistId = 2, isPrimary = false),
                crossRef(songId = 3, artistId = 3)
            )
        )

        assertThat(dao.getSongsForAlbums(listOf(10)).first().map { it.id })
            .containsExactly(1L, 2L)
        assertThat(dao.getSongsForArtists(listOf(1, 2)).first().map { it.id })
            .containsExactly(1L, 2L)
        Unit
    }

    private suspend fun seedLibrary(
        artists: List<ArtistEntity>,
        albums: List<AlbumEntity>,
        songs: List<SongEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        dao.insertMusicDataWithCrossRefs(songs, albums, artists, crossRefs)
    }

    private fun artist(id: Long, name: String) = ArtistEntity(
        id = id,
        name = name,
        trackCount = 1
    )

    private fun album(id: Long, title: String, artistId: Long) = AlbumEntity(
        id = id,
        title = title,
        artistName = "Artist $artistId",
        artistId = artistId,
        albumArtUriString = null,
        songCount = 1,
        year = 2026
    )

    private fun song(
        id: Long,
        title: String,
        artistId: Long,
        albumId: Long
    ) = SongEntity(
        id = id,
        title = title,
        artistName = "Artist $artistId",
        artistId = artistId,
        albumName = "Album $albumId",
        albumId = albumId,
        contentUriString = "content://songs/$id",
        albumArtUriString = null,
        duration = 180_000,
        genre = "Test",
        filePath = "/music/$id.mp3",
        parentDirectoryPath = "/music"
    )

    private fun crossRef(
        songId: Long,
        artistId: Long,
        isPrimary: Boolean = true
    ) = SongArtistCrossRef(
        songId = songId,
        artistId = artistId,
        isPrimary = isPrimary
    )
}
