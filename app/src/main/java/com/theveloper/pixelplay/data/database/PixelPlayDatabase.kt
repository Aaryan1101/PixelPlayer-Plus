package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumArtThemeEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        SongSearchFtsEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        TransitionRuleEntity::class,
        SongArtistCrossRef::class,
        RoomPlaylistEntity::class,
        PlaylistEntryEntity::class,
        OnlineMediaEntity::class,
        PlaybackEventEntity::class,
        SongEngagementEntity::class
    ],
    version = 15, // Moves playback history and engagement into Room
    exportSchema = true
)
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicDao(): MusicDao // Added MusicDao
    abstract fun transitionDao(): TransitionDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun engagementDao(): EngagementDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN parent_directory_path TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transition_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` TEXT NOT NULL,
                        `fromTrackId` TEXT,
                        `toTrackId` TEXT,
                        `mode` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `curveIn` TEXT NOT NULL,
                        `curveOut` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_transition_rules_playlistId_fromTrackId_toTrackId`
                    ON `transition_rules` (`playlistId`, `fromTrackId`, `toTrackId`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE albums ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN mime_type TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN sample_rate INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add album_artist column to songs table
                db.execSQL("ALTER TABLE songs ADD COLUMN album_artist TEXT DEFAULT NULL")
                
                // Create song_artist_cross_ref junction table for many-to-many relationship
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_artist_cross_ref (
                        song_id INTEGER NOT NULL,
                        artist_id INTEGER NOT NULL,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (song_id, artist_id),
                        FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
                        FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create indices for efficient queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_song_id ON song_artist_cross_ref(song_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_artist_id ON song_artist_cross_ref(artist_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_is_primary ON song_artist_cross_ref(is_primary)")
                
                // Migrate existing song-artist relationships to junction table
                // Each existing song gets its current artist as the primary artist
                db.execSQL("""
                    INSERT OR REPLACE INTO song_artist_cross_ref (song_id, artist_id, is_primary)
                    SELECT id, artist_id, 1 FROM songs WHERE artist_id IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add image_url column to artists table for Deezer artist images
                db.execSQL("ALTER TABLE artists ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_songs_file_path` ON `songs` (`file_path`)"
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                installSongsSearchArtifacts(db)
                rebuildSongsSearchIndex(db)
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `is_ai_generated` INTEGER NOT NULL,
                        `is_queue_generated` INTEGER NOT NULL,
                        `cover_image_uri` TEXT,
                        `cover_color_argb` INTEGER,
                        `cover_icon_name` TEXT,
                        `cover_shape_type` TEXT,
                        `cover_shape_detail_1` REAL,
                        `cover_shape_detail_2` REAL,
                        `cover_shape_detail_3` REAL,
                        `cover_shape_detail_4` REAL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `online_media` (
                        `media_key` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `source_id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT,
                        `duration` INTEGER NOT NULL,
                        `thumbnail_url` TEXT,
                        `last_metadata_update` INTEGER NOT NULL,
                        PRIMARY KEY(`media_key`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_entries` (
                        `playlist_id` TEXT NOT NULL,
                        `media_key` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        PRIMARY KEY(`playlist_id`, `media_key`),
                        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_online_media_source_source_id` " +
                        "ON `online_media` (`source`, `source_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_entries_playlist_id_position` " +
                        "ON `playlist_entries` (`playlist_id`, `position`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_entries_media_key` " +
                        "ON `playlist_entries` (`media_key`)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `media_key` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `started_at` INTEGER NOT NULL,
                        `ended_at` INTEGER NOT NULL,
                        `duration_listened_ms` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `song_engagement` (
                        `media_key` TEXT NOT NULL,
                        `play_count` INTEGER NOT NULL,
                        `total_listened_ms` INTEGER NOT NULL,
                        `last_played_at` INTEGER NOT NULL,
                        `skip_count` INTEGER NOT NULL,
                        PRIMARY KEY(`media_key`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playback_events_timestamp` " +
                        "ON `playback_events` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playback_events_media_key` " +
                        "ON `playback_events` (`media_key`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playback_events_media_key_timestamp` " +
                        "ON `playback_events` (`media_key`, `timestamp`)"
                )
            }
        }

        private fun createSongsSearchVirtualTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `songs_fts`
                USING FTS4(
                    `title`,
                    `artist_name`,
                    `album_name`,
                    tokenize=unicode61
                )
                """.trimIndent()
            )
        }

        private fun installSongsSearchArtifacts(db: SupportSQLiteDatabase) {
            createSongsSearchVirtualTable(db)

            db.execSQL("DROP TRIGGER IF EXISTS `trg_songs_fts_insert`")
            db.execSQL("DROP TRIGGER IF EXISTS `trg_songs_fts_update`")
            db.execSQL("DROP TRIGGER IF EXISTS `trg_songs_fts_delete`")

            db.execSQL(
                """
                CREATE TRIGGER `trg_songs_fts_insert`
                AFTER INSERT ON `songs`
                BEGIN
                    INSERT INTO `songs_fts` (`rowid`, `title`, `artist_name`, `album_name`)
                    VALUES (NEW.`id`, NEW.`title`, NEW.`artist_name`, NEW.`album_name`);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER `trg_songs_fts_update`
                AFTER UPDATE OF `id`, `title`, `artist_name`, `album_name` ON `songs`
                BEGIN
                    DELETE FROM `songs_fts` WHERE `rowid` = OLD.`id`;
                    INSERT INTO `songs_fts` (`rowid`, `title`, `artist_name`, `album_name`)
                    VALUES (NEW.`id`, NEW.`title`, NEW.`artist_name`, NEW.`album_name`);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER `trg_songs_fts_delete`
                AFTER DELETE ON `songs`
                BEGIN
                    DELETE FROM `songs_fts` WHERE `rowid` = OLD.`id`;
                END
                """.trimIndent()
            )
        }

        private fun rebuildSongsSearchIndex(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM `songs_fts`")
            db.execSQL(
                """
                INSERT INTO `songs_fts` (`rowid`, `title`, `artist_name`, `album_name`)
                SELECT `id`, `title`, `artist_name`, `album_name`
                FROM `songs`
                """.trimIndent()
            )
        }

        fun createSearchIndexCallback(): RoomDatabase.Callback {
            return object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    installSongsSearchArtifacts(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    installSongsSearchArtifacts(db)
                }
            }
        }
    }
}
