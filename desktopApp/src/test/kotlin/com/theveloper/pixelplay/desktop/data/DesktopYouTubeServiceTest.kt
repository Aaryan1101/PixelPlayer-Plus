package com.theveloper.pixelplay.desktop.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopYouTubeServiceTest {
    @Test
    fun `online search returns a resolvable audio stream`() = runBlocking {
        val service = DesktopYouTubeService()
        val results = service.search("Daft Punk Get Lucky").getOrThrow()

        assertTrue(results.isNotEmpty(), "Online search returned no music")
        val playback = service.resolvePlayback(results.first().path).getOrThrow()
        assertTrue(playback.audioUrl.startsWith("http"), "Extractor did not return an HTTP audio stream")
        assertTrue(playback.relatedSongs.isNotEmpty(), "Extractor did not return related songs for radio")
    }
}
