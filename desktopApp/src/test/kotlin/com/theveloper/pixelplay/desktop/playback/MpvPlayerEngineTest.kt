package com.theveloper.pixelplay.desktop.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertTrue

class MpvPlayerEngineTest {
    @Test
    fun `mpv starts and loads an audio file`() = runBlocking {
        val sampleRate = 8_000f
        val frameCount = 8_000L
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val audioBytes = ByteArray(frameCount.toInt() * format.frameSize)
        val audioFile = createTempFile("pixelplayer-playback-test", ".wav")
        AudioInputStream(ByteArrayInputStream(audioBytes), format, frameCount).use { audio ->
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, audioFile.toFile())
        }

        val engine = MpvPlayerEngine(onTrackEnded = {})
        try {
            assertTrue(engine.start().isSuccess, engine.state.value.error)
            engine.load(audioFile.toString())
            engine.setPaused(false)

            val loaded = withTimeout(5_000) {
                engine.state.first { it.durationMs >= 900 }
            }
            assertTrue(loaded.isReady)
        } finally {
            engine.close()
        }
    }
}
