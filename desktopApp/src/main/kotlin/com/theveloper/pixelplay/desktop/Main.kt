package com.theveloper.pixelplay.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.theveloper.pixelplay.desktop.integration.DesktopNotifier
import java.awt.Dimension
import java.awt.EventQueue
import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption

fun main() {
    val instanceLease = acquireInstanceLease()
    if (instanceLease == null) {
        raiseExistingInstance()
        return
    }

    try {
        application {
            var isWindowVisible by remember { mutableStateOf(true) }
            val controller = remember {
                DesktopController(
                    onRequestShow = { EventQueue.invokeLater { isWindowVisible = true } },
                    onRequestQuit = { EventQueue.invokeLater { exitApplication() } },
                )
            }
            var hasExplainedBackgroundPlayback by remember { mutableStateOf(false) }
            val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))

            DisposableEffect(controller) {
                onDispose(controller::close)
            }

            if (isTraySupported) {
                Tray(
                    icon = painterResource("icon.png"),
                    tooltip = "PixelPlayer",
                    menu = {
                        Item("Open PixelPlayer", onClick = { isWindowVisible = true })
                        Item("Play / Pause", onClick = controller::togglePlayPause)
                        Item("Next", onClick = { controller.playNext() })
                        Separator()
                        Item("Quit PixelPlayer", onClick = { exitApplication() })
                    },
                )
            }

            if (isWindowVisible) {
                Window(
                    onCloseRequest = {
                        isWindowVisible = false
                        if (!hasExplainedBackgroundPlayback) {
                            hasExplainedBackgroundPlayback = true
                            DesktopNotifier.showRunningInBackground()
                        }
                    },
                    title = "PixelPlayer",
                    state = windowState,
                ) {
                    LaunchedEffect(Unit) {
                        window.minimumSize = Dimension(960, 620)
                    }
                    PixelPlayerDesktopApp(controller)
                }
            }
        }
    } finally {
        instanceLease.close()
    }
}

private class InstanceLease(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        runCatching(lock::release)
        runCatching(channel::close)
    }
}

private fun acquireInstanceLease(): InstanceLease? {
    val lockPath = Path.of(
        System.getProperty("java.io.tmpdir"),
        "pixelplayer-desktop-${System.getProperty("user.name")}.lock",
    )
    val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = channel.tryLock()
    if (lock == null) {
        channel.close()
        return null
    }
    return InstanceLease(channel, lock)
}

private fun raiseExistingInstance() {
    repeat(8) {
        val exitCode = runCatching {
            ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.mpris.MediaPlayer2.PixelPlayer",
                "--object-path", "/org/mpris/MediaPlayer2",
                "--method", "org.mpris.MediaPlayer2.Raise",
            ).start().waitFor()
        }.getOrDefault(-1)
        if (exitCode == 0) return
        Thread.sleep(100)
    }
}
