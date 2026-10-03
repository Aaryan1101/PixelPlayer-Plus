package com.theveloper.pixelplay.desktop.integration

import kotlin.test.Test
import kotlin.test.assertEquals

class MprisServiceTest {
    @Test
    fun `service exports a PixelPlayer MPRIS endpoint on the session bus`() {
        val service = service()
        try {
            service.start().getOrThrow()
        } finally {
            service.close()
        }
    }

    @Test
    fun `next and previous media commands are routed to PixelPlayer callbacks`() {
        var nextCalls = 0
        var previousCalls = 0
        val service = service(
            onNext = { nextCalls++ },
            onPrevious = { previousCalls++ },
        )

        service.Next()
        service.Previous()

        assertEquals(1, nextCalls)
        assertEquals(1, previousCalls)
    }

    private fun service(
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
    ) = MprisService(
            MprisCallbacks(
                onNext = onNext,
                onPrevious = onPrevious,
                onPlayPause = {},
                onPlay = {},
                onPause = {},
                onStop = {},
                onSeekTo = {},
                onSetVolume = {},
            ),
        )
}
