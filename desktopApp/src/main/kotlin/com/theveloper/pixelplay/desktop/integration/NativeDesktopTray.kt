package com.theveloper.pixelplay.desktop.integration

import com.theveloper.pixelplay.desktop.DesktopUiState
import com.theveloper.pixelplay.desktop.RepeatMode
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.io.Closeable

class NativeDesktopTray(
    private val onOpen: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onToggleNotifications: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onQuit: () -> Unit,
) : Closeable {
    private val tray = runCatching {
        SystemTray.PREFER_GTK3 = true
        // GNOME does not expose the legacy GtkStatusIcon tray. Force the
        // StatusNotifier/AppIndicator backend used by its tray extension.
        SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.AppIndicator
        SystemTray.get("PixelPlayer")?.also { systemTray ->
            val icon = Thread.currentThread().contextClassLoader.getResource("icon.png")
            if (icon != null) systemTray.setImage(icon)
            systemTray.setTooltip("PixelPlayer")
        }
    }.getOrNull()

    private val openItem = MenuItem("Open PixelPlayer") { onOpen() }
    private val playPauseItem = MenuItem("Play") { onPlayPause() }
    private val previousItem = MenuItem("Previous") { onPrevious() }
    private val nextItem = MenuItem("Next") { onNext() }
    private val notificationsItem = MenuItem("Disable notifications") { onToggleNotifications() }
    private val settingsItem = MenuItem("Settings") { onOpenSettings() }
    private val quitItem = MenuItem("Quit PixelPlayer") { onQuit() }

    val isAvailable: Boolean get() = tray != null

    init {
        tray?.menu?.apply {
            add(openItem)
            add(Separator())
            add(playPauseItem)
            add(previousItem)
            add(nextItem)
            add(Separator())
            add(notificationsItem)
            add(settingsItem)
            add(Separator())
            add(quitItem)
        }
    }

    fun update(state: DesktopUiState) {
        val song = state.player.currentSong
        val tooltip = song
            ?.let { "${it.title} — ${it.displayArtist}" }
            ?.take(MAX_TOOLTIP_LENGTH)
            ?: "PixelPlayer"
        tray?.setTooltip(tooltip)
        playPauseItem.text = if (state.player.isPlaying) "Pause" else "Play"
        previousItem.enabled = song != null
        nextItem.enabled = when {
            state.queue.size > 1 && state.isShuffleEnabled -> true
            state.currentQueueIndex + 1 < state.queue.size -> true
            state.queue.isNotEmpty() && state.repeatMode == RepeatMode.ALL -> true
            else -> false
        }
        notificationsItem.text = if (state.notificationsEnabled) {
            "Disable notifications"
        } else {
            "Enable notifications"
        }
    }

    override fun close() {
        tray?.shutdown()
    }

    private companion object {
        const val MAX_TOOLTIP_LENGTH = 64
    }
}
