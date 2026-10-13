package com.theveloper.pixelplay.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationSqlSmokeTest {
    private lateinit var databaseHelper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun createDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()
        databaseHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        database = databaseHelper.writableDatabase
        database.setForeignKeyConstraintsEnabled(true)
    }

    @After
    fun closeDatabase() {
        databaseHelper.close()
    }

    @Test
    fun migration11To12_addsFilePathIndexWithoutChangingRows() {
        createSearchableSongsTable()
        database.execSQL(
            "INSERT INTO songs(id, file_path, title, artist_name, album_name) " +
                "VALUES (1, '/music/song.mp3', 'Song', 'Artist', 'Album')"
        )

        PixelPlayDatabase.MIGRATION_11_12.migrate(database)

        assertThat(indexNames("songs")).contains("index_songs_file_path")
        assertThat(singleLong("SELECT COUNT(*) FROM songs")).isEqualTo(1)
    }

    @Test
    fun migration12To13_buildsFtsAndKeepsItSynchronized() {
        createSearchableSongsTable()
        database.execSQL(
            "INSERT INTO songs(id, file_path, title, artist_name, album_name) " +
                "VALUES (1, '/music/one.mp3', 'Midnight Drive', 'Nova', 'Night')"
        )

        PixelPlayDatabase.MIGRATION_12_13.migrate(database)

        assertThat(matchCount("midnight")).isEqualTo(1)

        database.execSQL(
            "INSERT INTO songs(id, file_path, title, artist_name, album_name) " +
                "VALUES (2, '/music/two.mp3', 'Ocean Lights', 'Nova', 'Day')"
        )
        assertThat(matchCount("ocean")).isEqualTo(1)

        database.execSQL("UPDATE songs SET title = 'Sunrise Drive' WHERE id = 1")
        assertThat(matchCount("midnight")).isEqualTo(0)
        assertThat(matchCount("sunrise")).isEqualTo(1)

        database.execSQL("DELETE FROM songs WHERE id = 1")
        assertThat(matchCount("sunrise")).isEqualTo(0)
    }

    @Test
    fun migration13To14_createsMixedPlaylistTablesAndCascadeDelete() {
        PixelPlayDatabase.MIGRATION_13_14.migrate(database)

        assertThat(tableNames()).containsAtLeast(
            "playlists",
            "playlist_entries",
            "online_media"
        )
        assertThat(indexNames("online_media"))
            .contains("index_online_media_source_source_id")
        assertThat(indexNames("playlist_entries"))
            .containsAtLeast(
                "index_playlist_entries_playlist_id_position",
                "index_playlist_entries_media_key"
            )

        database.execSQL(
            """
            INSERT INTO playlists(
                id, name, created_at, updated_at, is_ai_generated, is_queue_generated
            ) VALUES ('playlist', 'Playlist', 1, 1, 0, 0)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO playlist_entries(playlist_id, media_key, position, added_at)
            VALUES ('playlist', 'youtube:abc', 0, 1)
            """.trimIndent()
        )

        database.execSQL("DELETE FROM playlists WHERE id = 'playlist'")
        assertThat(singleLong("SELECT COUNT(*) FROM playlist_entries")).isEqualTo(0)
    }

    @Test
    fun migration14To15_createsPlaybackTablesAndLookupIndices() {
        PixelPlayDatabase.MIGRATION_14_15.migrate(database)

        assertThat(tableNames()).containsAtLeast("playback_events", "song_engagement")
        assertThat(indexNames("playback_events"))
            .containsAtLeast(
                "index_playback_events_timestamp",
                "index_playback_events_media_key",
                "index_playback_events_media_key_timestamp"
            )

        database.execSQL(
            """
            INSERT INTO playback_events(
                media_key, timestamp, started_at, ended_at,
                duration_listened_ms, completed
            ) VALUES ('local:1', 2000, 1000, 2000, 1000, 1)
            """.trimIndent()
        )
        assertThat(singleLong("SELECT COUNT(*) FROM playback_events")).isEqualTo(1)
    }

    @Test
    fun migrationChain11To15_preservesLegacySongsAndCreatesNewStores() {
        createSearchableSongsTable()
        database.execSQL(
            "INSERT INTO songs(id, file_path, title, artist_name, album_name) " +
                "VALUES (42, '/legacy/song.flac', 'Legacy Song', 'Legacy Artist', 'Legacy Album')"
        )

        PixelPlayDatabase.MIGRATION_11_12.migrate(database)
        PixelPlayDatabase.MIGRATION_12_13.migrate(database)
        PixelPlayDatabase.MIGRATION_13_14.migrate(database)
        PixelPlayDatabase.MIGRATION_14_15.migrate(database)

        assertThat(singleLong("SELECT COUNT(*) FROM songs WHERE id = 42")).isEqualTo(1)
        assertThat(matchCount("legacy")).isEqualTo(1)
        assertThat(tableNames()).containsAtLeast(
            "playlists",
            "playlist_entries",
            "online_media",
            "playback_events",
            "song_engagement"
        )
    }

    private fun createSearchableSongsTable() {
        database.execSQL(
            """
            CREATE TABLE songs(
                id INTEGER PRIMARY KEY NOT NULL,
                file_path TEXT NOT NULL,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                album_name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun matchCount(term: String): Long {
        return singleLong(
            "SELECT COUNT(*) FROM songs_fts WHERE songs_fts MATCH ?",
            arrayOf(term)
        )
    }

    private fun tableNames(): List<String> {
        return queryStrings(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            columnIndex = 0
        )
    }

    private fun indexNames(table: String): List<String> {
        return queryStrings("PRAGMA index_list(`$table`)", columnIndex = 1)
    }

    private fun queryStrings(sql: String, columnIndex: Int): List<String> {
        return database.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(columnIndex))
            }
        }
    }

    private fun singleLong(sql: String, bindArgs: Array<Any> = emptyArray()): Long {
        return database.query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst()) { "Expected one result row for: $sql" }
            cursor.getLong(0)
        }
    }
}
