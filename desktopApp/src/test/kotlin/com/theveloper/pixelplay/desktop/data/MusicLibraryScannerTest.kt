package com.theveloper.pixelplay.desktop.data

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicLibraryScannerTest {
    @Test
    fun `scan finds supported files recursively and ignores other files`() = runBlocking {
        val library = createTempDirectory("pixelplayer-library-test")
        val album = library.resolve("Test Artist").createDirectories()
        album.resolve("Second Song.FLAC").createFile()
        album.resolve("First Song.mp3").createFile()
        album.resolve("cover.jpg").createFile()

        val songs = MusicLibraryScanner().scan(listOf(library))

        assertEquals(listOf("First Song", "Second Song"), songs.map { it.title })
        assertTrue(songs.all { it.artist == "Test Artist" })
    }

    @Test
    fun `scan de-duplicates the same folder`() = runBlocking {
        val library = createTempDirectory("pixelplayer-duplicate-library-test")
        library.resolve("Only Song.ogg").createFile()

        val songs = MusicLibraryScanner().scan(listOf(library, library))

        assertEquals(1, songs.size)
    }
}
