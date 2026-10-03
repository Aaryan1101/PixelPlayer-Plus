package com.theveloper.pixelplay.desktop.integration

import com.theveloper.pixelplay.desktop.RepeatMode
import com.theveloper.pixelplay.desktop.model.DesktopSong
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.io.Closeable
import kotlin.math.absoluteValue

data class MprisCallbacks(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSetVolume: (Int) -> Unit,
    val onRaise: () -> Unit = {},
    val onQuit: () -> Unit = {},
)

data class MprisState(
    val song: DesktopSong? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 72,
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}

class MprisService(
    private val callbacks: MprisCallbacks,
) : MprisRoot, MprisPlayer, Properties, Closeable {
    private var connection: DBusConnection? = null
    @Volatile private var state = MprisState()

    fun start(): Result<Unit> = runCatching {
        val dbus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        dbus.requestBusName(BUS_NAME)
        dbus.exportObject(OBJECT_PATH, this)
        connection = dbus
    }

    fun update(updated: MprisState) {
        val previous = state
        state = updated
        val changed = linkedMapOf<String, Variant<*>>()
        if (previous.song != updated.song) changed["Metadata"] = Variant(metadata(updated), "a{sv}")
        if (previous.isPlaying != updated.isPlaying || previous.song != updated.song) {
            changed["PlaybackStatus"] = Variant(playbackStatus(updated))
        }
        if (previous.volume != updated.volume) changed["Volume"] = Variant(updated.volume / 100.0)
        if (previous.canGoNext != updated.canGoNext) changed["CanGoNext"] = Variant(updated.canGoNext)
        if (previous.canGoPrevious != updated.canGoPrevious) changed["CanGoPrevious"] = Variant(updated.canGoPrevious)
        if (previous.shuffle != updated.shuffle) changed["Shuffle"] = Variant(updated.shuffle)
        if (previous.repeatMode != updated.repeatMode) changed["LoopStatus"] = Variant(loopStatus(updated.repeatMode))
        if (changed.isNotEmpty()) {
            runCatching {
                connection?.sendMessage(Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, changed, emptyList()))
            }
        }
    }

    override fun Next() = callbacks.onNext()
    override fun Previous() = callbacks.onPrevious()
    override fun Pause() = callbacks.onPause()
    override fun PlayPause() = callbacks.onPlayPause()
    override fun Stop() = callbacks.onStop()
    override fun Play() = callbacks.onPlay()
    override fun Seek(offset: Long) = callbacks.onSeekTo((state.positionMs + offset / 1_000L).coerceAtLeast(0L))
    override fun SetPosition(trackId: DBusPath, position: Long) = callbacks.onSeekTo((position / 1_000L).coerceAtLeast(0L))
    override fun OpenUri(uri: String) = Unit
    override fun Raise() = callbacks.onRaise()
    override fun Quit() = callbacks.onQuit()
    override fun getObjectPath(): String = OBJECT_PATH

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (properties(interfaceName)[propertyName] ?: error("Unknown MPRIS property: $propertyName")) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        val rawValue = if (value is Variant<*>) value.value else value
        when {
            interfaceName == PLAYER_INTERFACE && propertyName == "Volume" -> {
                val volume = ((rawValue as Number).toDouble() * 100.0).toInt().coerceIn(0, 100)
                callbacks.onSetVolume(volume)
            }
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties(interfaceName)

    private fun properties(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        ROOT_INTERFACE -> linkedMapOf(
            "CanQuit" to Variant(true),
            "CanRaise" to Variant(true),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("PixelPlayer"),
            "DesktopEntry" to Variant("pixelplayer-desktop"),
            "SupportedUriSchemes" to Variant(listOf("file", "http", "https"), "as"),
            "SupportedMimeTypes" to Variant(listOf("audio/mpeg", "audio/flac", "audio/ogg", "audio/mp4"), "as"),
        )
        PLAYER_INTERFACE -> linkedMapOf(
            "PlaybackStatus" to Variant(playbackStatus(state)),
            "LoopStatus" to Variant(loopStatus(state.repeatMode)),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(state.shuffle),
            "Metadata" to Variant(metadata(state), "a{sv}"),
            "Volume" to Variant(state.volume / 100.0),
            "Position" to Variant(state.positionMs * 1_000L),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(state.canGoNext),
            "CanGoPrevious" to Variant(state.canGoPrevious),
            "CanPlay" to Variant(true),
            "CanPause" to Variant(state.song != null),
            "CanSeek" to Variant(state.song != null),
            "CanControl" to Variant(true),
        )
        else -> emptyMap()
    }

    private fun metadata(current: MprisState): Map<String, Variant<*>> {
        val song = current.song
        val trackPath = if (song == null) {
            "/org/mpris/MediaPlayer2/TrackList/NoTrack"
        } else {
            "/com/theveloper/pixelplay/track/${song.id.hashCode().absoluteValue}"
        }
        return linkedMapOf<String, Variant<*>>(
            "mpris:trackid" to Variant(DBusPath(trackPath)),
        ).apply {
            if (song != null) {
                put("xesam:title", Variant(song.title))
                put("xesam:artist", Variant(listOf(song.displayArtist), "as"))
                put("xesam:album", Variant(song.displayAlbum))
                put("xesam:url", Variant(song.path))
                put("mpris:length", Variant(current.durationMs * 1_000L))
                song.artworkUrl?.let { put("mpris:artUrl", Variant(it)) }
            }
        }
    }

    private fun playbackStatus(current: MprisState): String = when {
        current.song == null -> "Stopped"
        current.isPlaying -> "Playing"
        else -> "Paused"
    }

    private fun loopStatus(mode: RepeatMode): String = when (mode) {
        RepeatMode.OFF -> "None"
        RepeatMode.ONE -> "Track"
        RepeatMode.ALL -> "Playlist"
    }

    override fun close() {
        runCatching { connection?.unExportObject(OBJECT_PATH) }
        runCatching { connection?.releaseBusName(BUS_NAME) }
        runCatching { connection?.close() }
        connection = null
    }

    private companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.PixelPlayer"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
    }
}
