package com.theveloper.pixelplay.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.theveloper.pixelplay.desktop.integration.DesktopNotifier
import com.theveloper.pixelplay.desktop.integration.NativeDesktopTray
import com.theveloper.pixelplay.desktop.model.DesktopDestination
import java.awt.Dimension
import java.awt.EventQueue
import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main() {
    val instanceLease = acquireInstanceLease()
    if (instanceLease == null) {
        raiseExistingInstance()
        return
    }

    try {
        application {
            var isWindowVisible by remember { mutableStateOf(true) }
            var quitRequested by remember { mutableStateOf(false) }
            val controller = remember {
                DesktopController(
                    onRequestShow = { EventQueue.invokeLater { isWindowVisible = true } },
                    onRequestQuit = { EventQueue.invokeLater { quitRequested = true } },
                )
            }
            val uiState by controller.state.collectAsState()
            var hasExplainedBackgroundPlayback by remember { mutableStateOf(false) }
            val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))
            val tray = remember {
                NativeDesktopTray(
                    onOpen = { EventQueue.invokeLater { isWindowVisible = true } },
                    onPlayPause = controller::togglePlayPause,
                    onPrevious = controller::playPrevious,
                    onNext = { controller.playNext() },
                    onToggleNotifications = {
                        controller.setNotificationsEnabled(!controller.state.value.notificationsEnabled)
                    },
                    onOpenSettings = {
                        EventQueue.invokeLater {
                            controller.navigate(DesktopDestination.SETTINGS)
                            isWindowVisible = true
                        }
                    },
                    onQuit = { EventQueue.invokeLater { quitRequested = true } },
                )
            }

            LaunchedEffect(quitRequested) {
                if (quitRequested) {
                    controller.close()
                    thread(name = "pixelplayer-exit-fallback", isDaemon = true) {
                        Thread.sleep(1_500)
                        exitProcess(0)
                    }
                    exitApplication()
                }
            }

            DisposableEffect(controller) {
                onDispose(controller::close)
            }

            LaunchedEffect(uiState) {
                tray.update(uiState)
            }

            DisposableEffect(tray) {
                onDispose(tray::close)
            }

            if (isWindowVisible) {
                Window(
                    onCloseRequest = {
                        if (tray.isAvailable && uiState.closeToTray) {
                            isWindowVisible = false
                            if (
                                !hasExplainedBackgroundPlayback &&
                                uiState.notificationsEnabled &&
                                uiState.backgroundNotifications
                            ) {
                                hasExplainedBackgroundPlayback = true
                                DesktopNotifier.showRunningInBackground()
                            }
                        } else {
                            quitRequested = true
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
