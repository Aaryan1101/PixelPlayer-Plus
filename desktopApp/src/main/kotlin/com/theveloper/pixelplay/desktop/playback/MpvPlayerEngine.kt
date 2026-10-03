package com.theveloper.pixelplay.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

data class MpvSnapshot(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 72,
    val error: String? = null,
)

class MpvPlayerEngine(
    private val onTrackEnded: () -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val requestId = AtomicInteger(10)
    private val socketPath = Path.of(
        System.getProperty("java.io.tmpdir"),
        "pixelplayer-${ProcessHandle.current().pid()}.sock",
    )

    private var process: Process? = null
    private var channel: SocketChannel? = null
    private val writeLock = Any()
    private val _state = MutableStateFlow(MpvSnapshot())
    val state: StateFlow<MpvSnapshot> = _state.asStateFlow()

    suspend fun start(): Result<Unit> = runCatching {
        checkMpvAvailable()
        Files.deleteIfExists(socketPath)
        val arguments = buildList {
            add("mpv")
            add("--idle=yes")
            add("--no-video")
            add("--force-window=no")
            add("--audio-display=no")
            add("--keep-open=no")
            add("--no-terminal")
            add("--input-ipc-server=$socketPath")
        }
        process = ProcessBuilder(arguments).redirectErrorStream(true).start().also(::drainProcessOutput)

        repeat(60) {
            if (Files.exists(socketPath)) return@repeat
            if (process?.isAlive != true) error("mpv stopped before its control socket was ready")
            delay(50)
        }
        check(Files.exists(socketPath)) { "Timed out while starting mpv" }

        channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
            connect(UnixDomainSocketAddress.of(socketPath))
        }
        startReader(channel!!)
        observe("time-pos", 1)
        observe("duration", 2)
        observe("pause", 3)
        observe("volume", 4)
        _state.value = _state.value.copy(isReady = true, error = null)
    }.onFailure { error ->
        _state.value = _state.value.copy(error = error.message ?: "Unable to start mpv")
    }

    fun load(path: String, displayTitle: String? = null) {
        command("loadfile", path, "replace")
        if (!displayTitle.isNullOrBlank()) {
            command("set_property", "force-media-title", displayTitle)
        }
    }

    fun togglePause() {
        command("cycle", "pause")
    }

    fun setPaused(paused: Boolean) {
        command("set_property", "pause", paused)
    }

    fun seekTo(positionMs: Long) {
        command("seek", positionMs.coerceAtLeast(0L) / 1_000.0, "absolute", "exact")
    }

    fun setVolume(volume: Int) {
        command("set_property", "volume", volume.coerceIn(0, 100))
    }

    fun stop() {
        command("stop")
    }

    private fun observe(property: String, id: Int) {
        command("observe_property", id, property)
    }

    private fun command(vararg values: Any?) {
        val socket = channel ?: return
        val payload = buildJsonObject {
            put("command", buildJsonArray { values.forEach { add(toJson(it)) } })
            put("request_id", requestId.incrementAndGet())
        }.toString() + "\n"
        runCatching {
            synchronized(writeLock) {
                val buffer = ByteBuffer.wrap(payload.toByteArray(StandardCharsets.UTF_8))
                while (buffer.hasRemaining()) socket.write(buffer)
            }
        }.onFailure { error ->
            _state.value = _state.value.copy(error = "Playback command failed: ${error.message}")
        }
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun startReader(socket: SocketChannel) {
        scope.launch {
            runCatching {
                BufferedReader(InputStreamReader(Channels.newInputStream(socket))).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        handleMessage(line)
                    }
                }
            }.onFailure { error ->
                if (process?.isAlive == true) {
                    _state.value = _state.value.copy(error = "Lost connection to mpv: ${error.message}")
                }
            }
        }
    }

    private fun handleMessage(line: String) {
        val message = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        when (message["event"]?.jsonPrimitive?.contentOrNull) {
            "property-change" -> {
                val name = message["name"]?.jsonPrimitive?.contentOrNull ?: return
                val data = message["data"]
                _state.value = when (name) {
                    "time-pos" -> _state.value.copy(positionMs = secondsToMs(data))
                    "duration" -> _state.value.copy(durationMs = secondsToMs(data))
                    "pause" -> _state.value.copy(
                        isPlaying = !(data?.jsonPrimitive?.booleanOrNull ?: true),
                    )
                    "volume" -> _state.value.copy(
                        volume = (data?.jsonPrimitive?.doubleOrNull ?: 72.0).toInt().coerceIn(0, 100),
                    )
                    else -> _state.value
                }
            }
            "end-file" -> {
                val reason = message["reason"]?.jsonPrimitive?.contentOrNull
                if (reason == "eof") onTrackEnded()
            }
        }
    }

    private fun secondsToMs(element: JsonElement?): Long =
        ((element?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1_000.0).toLong().coerceAtLeast(0L)

    private fun checkMpvAvailable() {
        val probe = ProcessBuilder("mpv", "--version").redirectErrorStream(true).start()
        check(probe.waitFor() == 0) { "mpv is required for desktop playback" }
    }

    private fun drainProcessOutput(started: Process) {
        scope.launch {
            started.inputStream.bufferedReader().useLines { lines -> lines.forEach { _ -> } }
        }
    }

    override fun close() {
        runCatching { command("quit") }
        runCatching { channel?.close() }
        runCatching { process?.destroy() }
        runCatching { Files.deleteIfExists(socketPath) }
        scope.cancel()
    }
}
