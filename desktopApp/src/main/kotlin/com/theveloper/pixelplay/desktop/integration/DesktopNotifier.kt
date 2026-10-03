package com.theveloper.pixelplay.desktop.integration

import com.theveloper.pixelplay.desktop.model.DesktopSong

object DesktopNotifier {
    fun showNowPlaying(song: DesktopSong) {
        runCatching {
            ProcessBuilder(
                "notify-send",
                "--app-name=PixelPlayer",
                "--icon=pixelplayer-desktop",
                "--hint=string:x-canonical-private-synchronous:pixelplayer-now-playing",
                "Now playing",
                "${song.title}\n${song.displayArtist}",
            ).start()
        }
    }

    fun showRunningInBackground() {
        runCatching {
            ProcessBuilder(
                "notify-send",
                "--app-name=PixelPlayer",
                "--icon=pixelplayer-desktop",
                "--hint=string:x-canonical-private-synchronous:pixelplayer-background",
                "PixelPlayer is still playing",
                "Use the system tray or media controls to reopen or quit.",
            ).start()
        }
    }
}
