package com.theveloper.pixelplay.desktop.data

import com.theveloper.pixelplay.desktop.model.DesktopPlaylist
import com.theveloper.pixelplay.desktop.model.DesktopPreferences
import com.theveloper.pixelplay.desktop.model.DesktopSong
import com.theveloper.pixelplay.desktop.model.ListeningHistoryEntry
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPreferencesStoreTest {
    @Test
    fun `preferences survive a save and load round trip`() {
        val directory = createTempDirectory("pixelplayer-preferences-test")
        val store = DesktopPreferencesStore(directory.resolve("preferences.json"))
        val onlineSong = DesktopSong(
            path = "https://youtube.com/watch?v=test",
            title = "Test Song",
            artist = "Test Artist",
            album = "Online",
            isOnline = true,
        )
        val preferences = DesktopPreferences(
            libraryFolders = listOf("/music/one", "/music/two"),
            favoritePaths = setOf("/music/one/favorite.flac"),
            favoriteSongs = listOf(onlineSong),
            playlists = listOf(
                DesktopPlaylist(
                    id = "focus",
                    name = "Focus",
                    songPaths = listOf("/music/one/favorite.flac"),
                    songs = listOf(onlineSong),
                ),
            ),
            listeningHistory = listOf(
                ListeningHistoryEntry(
                    song = onlineSong,
                    playedAt = 123456789L,
                    playCount = 3,
                ),
            ),
            volume = 41,
            lastSongPath = "/music/one/favorite.flac",
        )

        store.save(preferences)

        assertEquals(preferences, store.load())
    }

    @Test
    fun `invalid preferences safely fall back to defaults`() {
        val directory = createTempDirectory("pixelplayer-invalid-preferences-test")
        val file = directory.resolve("preferences.json")
        file.toFile().writeText("not-json")

        assertEquals(DesktopPreferences(), DesktopPreferencesStore(file).load())
    }
}
