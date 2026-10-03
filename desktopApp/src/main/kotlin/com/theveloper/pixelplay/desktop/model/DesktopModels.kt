package com.theveloper.pixelplay.desktop.model

import kotlinx.serialization.Serializable

@Serializable
data class DesktopSong(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val isOnline: Boolean = false,
    val artworkUrl: String? = null,
) {
    val id: String get() = path
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown album" }
}

@Serializable
data class DesktopPlaylist(
    val id: String,
    val name: String,
    val songPaths: List<String> = emptyList(),
    val songs: List<DesktopSong> = emptyList(),
)

@Serializable
data class ListeningHistoryEntry(
    val song: DesktopSong,
    val playedAt: Long,
    val playCount: Int = 1,
)

@Serializable
data class DesktopPreferences(
    val libraryFolders: List<String> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
    val favoriteSongs: List<DesktopSong> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val listeningHistory: List<ListeningHistoryEntry> = emptyList(),
    val volume: Int = 72,
    val lastSongPath: String? = null,
)

data class PlayerSnapshot(
    val currentSong: DesktopSong? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 72,
    val error: String? = null,
)

enum class DesktopDestination(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Your Library"),
    LIKED("Liked Songs"),
}
