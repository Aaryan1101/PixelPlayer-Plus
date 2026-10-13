package com.theveloper.pixelplay.data.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class RoomPlaylistStore @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val migrationMutex = Mutex()

    private val legacyPlaylistsKey = stringPreferencesKey("user_playlists_json_v1")
    private val legacyRemoteSongsKey = stringPreferencesKey("saved_remote_songs_json_v1")
    private val migrationCompleteKey = booleanPreferencesKey("playlists_migrated_to_room_v1")

    val playlists: Flow<List<Playlist>> = flow {
        ensureMigrated()
        emitAll(
            playlistDao.observePlaylists().map { playlists ->
                playlists.map { playlist -> playlist.toModel() }
            }
        )
    }

    suspend fun ensureMigrated() {
        migrationMutex.withLock {
            val preferences = dataStore.data.first()
            if (preferences[migrationCompleteKey] == true) return

            val legacyPlaylists = preferences[legacyPlaylistsKey]
                ?.takeIf(String::isNotBlank)
                ?.let { encoded ->
                    runCatching { json.decodeFromString<List<Playlist>>(encoded) }
                        .onFailure { Timber.e(it, "Ignoring unreadable legacy playlist JSON") }
                        .getOrDefault(emptyList())
                }
                .orEmpty()
            val legacyRemoteSongs = preferences[legacyRemoteSongsKey]
                ?.takeIf(String::isNotBlank)
                ?.let { encoded ->
                    runCatching { json.decodeFromString<List<LegacyRemoteSong>>(encoded) }
                        .onFailure { Timber.e(it, "Ignoring unreadable legacy remote-song JSON") }
                        .getOrDefault(emptyList())
                }
                .orEmpty()

            val playlistEntities = legacyPlaylists.map { playlist -> playlist.toEntity() }
            val entryEntities = legacyPlaylists.flatMap { playlist ->
                playlist.songIds.distinct().mapIndexed { position, songId ->
                    PlaylistEntryEntity(
                        playlistId = playlist.id,
                        mediaKey = songId.toStableMediaKey(),
                        position = position,
                        addedAt = playlist.createdAt
                    )
                }
            }
            val onlineMedia = legacyRemoteSongs
                .filterNot { it.id.toLongOrNull() != null }
                .distinctBy { it.id }
                .map { song -> song.toEntity() }

            playlistDao.importLegacyData(playlistEntities, entryEntities, onlineMedia)
            dataStore.edit { it[migrationCompleteKey] = true }
        }
    }

    suspend fun create(playlist: Playlist) {
        ensureMigrated()
        playlistDao.createPlaylistWithEntries(
            playlist = playlist.toEntity(),
            entries = playlist.toEntries()
        )
    }

    suspend fun search(query: String, limit: Int): List<Playlist> {
        if (query.isBlank() || limit <= 0) return emptyList()
        ensureMigrated()
        return playlistDao.searchPlaylists(query.trim(), limit)
            .map { playlist -> playlist.toModel() }
    }

    suspend fun delete(playlistId: String) {
        ensureMigrated()
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun rename(playlistId: String, name: String) {
        ensureMigrated()
        playlistDao.renamePlaylist(playlistId, name, System.currentTimeMillis())
    }

    suspend fun update(playlist: Playlist) {
        ensureMigrated()
        val updated = playlist.copy(lastModified = System.currentTimeMillis())
        playlistDao.replacePlaylist(updated.toEntity(), updated.toEntries())
    }

    suspend fun addSongs(playlistId: String, songIds: List<String>) {
        ensureMigrated()
        playlistDao.addMedia(
            playlistId = playlistId,
            mediaKeys = songIds.map { it.toStableMediaKey() },
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun synchronizeSongMembership(songId: String, playlistIds: List<String>): List<String> {
        ensureMigrated()
        return playlistDao.synchronizeMediaMembership(
            mediaKey = songId.toStableMediaKey(),
            targetPlaylistIds = playlistIds,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeSong(playlistId: String, songId: String) {
        ensureMigrated()
        playlistDao.removeMedia(
            playlistId = playlistId,
            mediaKey = songId.toStableMediaKey(),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeSongFromAll(songId: String) {
        ensureMigrated()
        playlistDao.removeMediaFromAllPlaylists(
            mediaKey = songId.toStableMediaKey(),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun reorder(playlistId: String, songIds: List<String>) {
        ensureMigrated()
        playlistDao.reorderPlaylist(
            playlistId = playlistId,
            orderedMediaKeys = songIds.map { it.toStableMediaKey() },
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveOnlineSong(song: Song) {
        if (song.id.toLongOrNull() != null) return
        ensureMigrated()
        playlistDao.upsertOnlineMedia(listOf(song.toOnlineMediaEntity()))
    }

    private fun Playlist.toEntity(): RoomPlaylistEntity {
        return RoomPlaylistEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = lastModified,
            isAiGenerated = isAiGenerated,
            isQueueGenerated = isQueueGenerated,
            coverImageUri = coverImageUri,
            coverColorArgb = coverColorArgb,
            coverIconName = coverIconName,
            coverShapeType = coverShapeType,
            coverShapeDetail1 = coverShapeDetail1,
            coverShapeDetail2 = coverShapeDetail2,
            coverShapeDetail3 = coverShapeDetail3,
            coverShapeDetail4 = coverShapeDetail4
        )
    }

    private fun Playlist.toEntries(): List<PlaylistEntryEntity> {
        return songIds.distinct().mapIndexed { position, songId ->
            PlaylistEntryEntity(
                playlistId = id,
                mediaKey = songId.toStableMediaKey(),
                position = position,
                addedAt = lastModified
            )
        }
    }

    private fun PlaylistWithEntries.toModel(): Playlist {
        val orderedSongIds = entries
            .sortedBy(PlaylistEntryEntity::position)
            .map { it.mediaKey.toLegacySongId() }
        return Playlist(
            id = playlist.id,
            name = playlist.name,
            songIds = orderedSongIds,
            createdAt = playlist.createdAt,
            lastModified = playlist.updatedAt,
            isAiGenerated = playlist.isAiGenerated,
            isQueueGenerated = playlist.isQueueGenerated,
            coverImageUri = playlist.coverImageUri,
            coverColorArgb = playlist.coverColorArgb,
            coverIconName = playlist.coverIconName,
            coverShapeType = playlist.coverShapeType,
            coverShapeDetail1 = playlist.coverShapeDetail1,
            coverShapeDetail2 = playlist.coverShapeDetail2,
            coverShapeDetail3 = playlist.coverShapeDetail3,
            coverShapeDetail4 = playlist.coverShapeDetail4
        )
    }

    private fun Song.toOnlineMediaEntity(): OnlineMediaEntity {
        val mediaKey = id.toStableMediaKey()
        val source = mediaKey.substringBefore(':')
        val sourceId = mediaKey.substringAfter(':', id)
        return OnlineMediaEntity(
            mediaKey = mediaKey,
            source = source,
            sourceId = sourceId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            thumbnailUrl = albumArtUriString,
            lastMetadataUpdate = System.currentTimeMillis()
        )
    }

    private fun LegacyRemoteSong.toEntity(): OnlineMediaEntity {
        val mediaKey = id.toStableMediaKey()
        return OnlineMediaEntity(
            mediaKey = mediaKey,
            source = mediaKey.substringBefore(':'),
            sourceId = mediaKey.substringAfter(':', id),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            thumbnailUrl = albumArtUriString,
            lastMetadataUpdate = System.currentTimeMillis()
        )
    }

    @Serializable
    private data class LegacyRemoteSong(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtUriString: String? = null,
        val duration: Long
    )
}
