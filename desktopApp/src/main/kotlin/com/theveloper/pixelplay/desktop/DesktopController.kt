package com.theveloper.pixelplay.desktop

import com.theveloper.pixelplay.desktop.data.DesktopPreferencesStore
import com.theveloper.pixelplay.desktop.data.MusicLibraryScanner
import com.theveloper.pixelplay.desktop.data.DesktopYouTubeService
import com.theveloper.pixelplay.desktop.model.DesktopDestination
import com.theveloper.pixelplay.desktop.model.DesktopPlaylist
import com.theveloper.pixelplay.desktop.model.DesktopPreferences
import com.theveloper.pixelplay.desktop.model.DesktopSong
import com.theveloper.pixelplay.desktop.model.ListeningHistoryEntry
import com.theveloper.pixelplay.desktop.model.PlayerSnapshot
import com.theveloper.pixelplay.desktop.integration.DesktopNotifier
import com.theveloper.pixelplay.desktop.integration.MprisCallbacks
import com.theveloper.pixelplay.desktop.integration.MprisService
import com.theveloper.pixelplay.desktop.integration.MprisState
import com.theveloper.pixelplay.desktop.playback.MpvPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class DesktopUiState(
    val destination: DesktopDestination = DesktopDestination.HOME,
    val selectedPlaylistId: String? = null,
    val songs: List<DesktopSong> = emptyList(),
    val queue: List<DesktopSong> = emptyList(),
    val currentQueueIndex: Int = -1,
    val player: PlayerSnapshot = PlayerSnapshot(),
    val favoritePaths: Set<String> = emptySet(),
    val favoriteSongs: List<DesktopSong> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val listeningHistory: List<ListeningHistoryEntry> = emptyList(),
    val recommendedSongs: List<DesktopSong> = emptyList(),
    val homeShelves: Map<String, List<DesktopSong>> = emptyMap(),
    val isLoadingHome: Boolean = false,
    val libraryFolders: List<String> = emptyList(),
    val query: String = "",
    val onlineResults: List<DesktopSong> = emptyList(),
    val isOnlineSearching: Boolean = false,
    val onlineError: String? = null,
    val isScanning: Boolean = false,
    val statusMessage: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

enum class RepeatMode { OFF, ALL, ONE }

class DesktopController(
    private val preferencesStore: DesktopPreferencesStore = DesktopPreferencesStore(),
    private val scanner: MusicLibraryScanner = MusicLibraryScanner(),
    private val youtubeService: DesktopYouTubeService = DesktopYouTubeService(),
    private val onRequestShow: () -> Unit = {},
    private val onRequestQuit: () -> Unit = {},
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var preferences = preferencesStore.load()
    private val playerEngine = MpvPlayerEngine(onTrackEnded = { scope.launch { playNext(fromTrackEnd = true) } })
    private val mprisService = MprisService(
        MprisCallbacks(
            onNext = { scope.launch { playNext() } },
            onPrevious = { scope.launch { playPrevious() } },
            onPlayPause = { scope.launch { togglePlayPause() } },
            onPlay = { scope.launch { resumePlayback() } },
            onPause = { scope.launch { pausePlayback() } },
            onStop = { scope.launch { stopPlayback() } },
            onSeekTo = { position -> scope.launch { seekTo(position) } },
            onSetVolume = { volume -> scope.launch { setVolume(volume) } },
            onRaise = onRequestShow,
            onQuit = onRequestQuit,
        ),
    )
    private var onlineSearchJob: Job? = null

    private val _state = MutableStateFlow(
        DesktopUiState(
            favoritePaths = preferences.favoritePaths,
            favoriteSongs = preferences.favoriteSongs,
            playlists = preferences.playlists,
            listeningHistory = preferences.listeningHistory,
            libraryFolders = initialFolders(preferences.libraryFolders),
            player = PlayerSnapshot(volume = preferences.volume),
        ),
    )
    val state: StateFlow<DesktopUiState> = _state.asStateFlow()

    init {
        mprisService.start().onFailure { error ->
            error.printStackTrace()
            _state.update { it.copy(statusMessage = "System media controls unavailable: ${error.message}") }
        }
        scope.launch {
            playerEngine.start().onFailure { error ->
                _state.update { it.copy(statusMessage = error.message) }
            }
            playerEngine.setVolume(preferences.volume)
        }
        scope.launch {
            playerEngine.state.collect { mpv ->
                _state.update { current ->
                    current.copy(
                        player = current.player.copy(
                            isPlaying = current.player.currentSong != null && mpv.isPlaying,
                            positionMs = mpv.positionMs,
                            durationMs = mpv.durationMs.takeIf { it > 0 }
                                ?: current.player.currentSong?.durationMs.orZero(),
                            volume = mpv.volume,
                            error = mpv.error,
                        ),
                    )
                }
            }
        }
        scope.launch {
            state.collect { current ->
                mprisService.update(
                    MprisState(
                        song = current.player.currentSong,
                        isPlaying = current.player.isPlaying,
                        positionMs = current.player.positionMs,
                        durationMs = current.player.durationMs,
                        volume = current.player.volume,
                        canGoNext = current.currentQueueIndex + 1 < current.queue.size,
                        canGoPrevious = current.player.currentSong != null,
                        shuffle = current.isShuffleEnabled,
                        repeatMode = current.repeatMode,
                    ),
                )
            }
        }
        refreshLibrary()
        refreshHomeSuggestions()
    }

    fun navigate(destination: DesktopDestination) {
        _state.update { it.copy(destination = destination, selectedPlaylistId = null) }
    }

    fun selectPlaylist(playlistId: String) {
        _state.update { it.copy(selectedPlaylistId = playlistId) }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        onlineSearchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update {
                it.copy(onlineResults = emptyList(), isOnlineSearching = false, onlineError = null)
            }
            return
        }
        onlineSearchJob = scope.launch {
            delay(450)
            _state.update { it.copy(isOnlineSearching = true, onlineError = null) }
            youtubeService.search(query.trim())
                .onSuccess { results ->
                    _state.update {
                        it.copy(onlineResults = results, isOnlineSearching = false, onlineError = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            onlineResults = emptyList(),
                            isOnlineSearching = false,
                            onlineError = error.message ?: "Online search failed",
                        )
                    }
                }
        }
    }

    fun refreshLibrary() {
        if (_state.value.isScanning) return
        scope.launch {
            _state.update { it.copy(isScanning = true, statusMessage = "Scanning your music library…") }
            val paths = _state.value.libraryFolders.map(Path::of)
            runCatching { scanner.scan(paths) }
                .onSuccess { songs ->
                    _state.update {
                        it.copy(
                            songs = songs,
                            isScanning = false,
                            statusMessage = if (songs.isEmpty()) {
                                "Choose a music folder to begin"
                            } else {
                                "${songs.size} songs ready"
                            },
                        )
                    }
                    restoreLastSong(songs)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isScanning = false, statusMessage = "Library scan failed: ${error.message}")
                    }
                }
        }
    }

    fun refreshHomeSuggestions() {
        if (_state.value.isLoadingHome) return
        scope.launch {
            _state.update { it.copy(isLoadingHome = true) }
            val favoriteArtist = _state.value.listeningHistory
                .groupBy { it.song.displayArtist }
                .maxByOrNull { (_, entries) -> entries.sumOf(ListeningHistoryEntry::playCount) }
                ?.key
            val searches = linkedMapOf(
                "Trending now" to "trending music",
                "Made for your mood" to "chill music mix",
                "Focus flow" to "focus music",
                "Fresh pop" to "new pop music",
            ).let { base ->
                if (favoriteArtist == null) base else linkedMapOf(
                    "More like $favoriteArtist" to "$favoriteArtist songs",
                ) + base
            }
            val shelves = linkedMapOf<String, List<DesktopSong>>()
            searches.forEach { (title, query) ->
                val rawSongs = youtubeService.search(query).getOrDefault(emptyList())
                val songs = rawSongs.filter { it.durationMs in 90_000L..600_000L }.take(10)
                if (songs.isNotEmpty()) shelves[title] = songs
            }
            _state.update { current ->
                current.copy(
                    homeShelves = shelves,
                    isLoadingHome = false,
                    statusMessage = if (shelves.isNotEmpty() && current.songs.isEmpty()) {
                        "Online music ready"
                    } else {
                        current.statusMessage
                    },
                )
            }
        }
    }

    fun addLibraryFolder(path: String) {
        val normalized = Path.of(path).toAbsolutePath().normalize().toString()
        if (!Files.isDirectory(Path.of(normalized))) return
        val folders = (_state.value.libraryFolders + normalized).distinct()
        _state.update { it.copy(libraryFolders = folders) }
        persist { copy(libraryFolders = folders) }
        refreshLibrary()
    }

    fun playSong(song: DesktopSong, source: List<DesktopSong> = _state.value.songs) {
        val queue = if (song.isOnline && source !== _state.value.queue) {
            listOf(song)
        } else {
            source.ifEmpty { listOf(song) }
        }
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _state.update {
            it.copy(
                queue = queue,
                currentQueueIndex = index,
                player = it.player.copy(
                    currentSong = song,
                    positionMs = 0,
                    durationMs = song.durationMs,
                    error = null,
                ),
            )
        }
        if (!song.isOnline) {
            preferences = preferences.copy(lastSongPath = song.path)
            persistNow()
            playerEngine.load(song.path, song.title)
            playerEngine.setPaused(false)
            recordPlay(song)
            DesktopNotifier.showNowPlaying(song)
        } else {
            _state.update { it.copy(statusMessage = "Connecting to ${song.title}…") }
            scope.launch {
                youtubeService.resolvePlayback(song.path)
                    .onSuccess { playback ->
                        playerEngine.load(playback.audioUrl, song.title)
                        playerEngine.setPaused(false)
                        recordPlay(song)
                        DesktopNotifier.showNowPlaying(song)
                        val recommendations = rankRecommendations(song, playback.relatedSongs)
                        _state.update { current ->
                            if (current.player.currentSong?.id != song.id) current else {
                                val existing = current.queue.mapTo(mutableSetOf(), DesktopSong::id)
                                current.copy(
                                    queue = current.queue + recommendations.filter { existing.add(it.id) },
                                    recommendedSongs = recommendations,
                                    statusMessage = "Radio based on ${song.title}",
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                statusMessage = "Could not play ${song.title}",
                                player = it.player.copy(error = error.message ?: "Online playback failed"),
                            )
                        }
                    }
            }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.player.currentSong == null) {
            current.songs.firstOrNull()?.let(::playSong)
        } else {
            playerEngine.togglePause()
        }
    }

    fun resumePlayback() {
        val current = _state.value
        if (current.player.currentSong == null) {
            val first = current.listeningHistory.firstOrNull()?.song
                ?: current.homeShelves.values.firstNotNullOfOrNull { it.firstOrNull() }
                ?: current.songs.firstOrNull()
            first?.let { playSong(it, listOf(it)) }
        } else {
            playerEngine.setPaused(false)
        }
    }

    fun pausePlayback() = playerEngine.setPaused(true)

    fun stopPlayback() {
        playerEngine.stop()
        _state.update {
            it.copy(player = it.player.copy(isPlaying = false, positionMs = 0L))
        }
    }

    fun playNext(fromTrackEnd: Boolean = false) {
        val current = _state.value
        if (current.queue.isEmpty()) return
        if (fromTrackEnd && current.repeatMode == RepeatMode.ONE) {
            current.player.currentSong?.let { playSong(it, current.queue) }
            return
        }
        val nextIndex = when {
            current.isShuffleEnabled && current.queue.size > 1 -> {
                current.queue.indices.filter { it != current.currentQueueIndex }.random()
            }
            current.currentQueueIndex + 1 < current.queue.size -> current.currentQueueIndex + 1
            current.repeatMode == RepeatMode.ALL -> 0
            else -> return
        }
        playSong(current.queue[nextIndex], current.queue)
    }

    fun playPrevious() {
        val current = _state.value
        if (current.player.positionMs > 4_000) {
            playerEngine.seekTo(0)
            return
        }
        if (current.queue.isEmpty()) return
        val previousIndex = (current.currentQueueIndex - 1).coerceAtLeast(0)
        playSong(current.queue[previousIndex], current.queue)
    }

    fun seekTo(positionMs: Long) = playerEngine.seekTo(positionMs)

    fun setVolume(volume: Int) {
        val bounded = volume.coerceIn(0, 100)
        playerEngine.setVolume(bounded)
        persist { copy(volume = bounded) }
    }

    fun toggleFavorite(song: DesktopSong) {
        val isAdding = song.path !in _state.value.favoritePaths
        val favorites = _state.value.favoritePaths.toMutableSet().apply {
            if (isAdding) add(song.path) else remove(song.path)
        }
        val favoriteSongs = if (isAdding) {
            (listOf(song) + _state.value.favoriteSongs.filterNot { it.path == song.path }).take(500)
        } else {
            _state.value.favoriteSongs.filterNot { it.path == song.path }
        }
        _state.update { it.copy(favoritePaths = favorites, favoriteSongs = favoriteSongs) }
        persist { copy(favoritePaths = favorites, favoriteSongs = favoriteSongs) }
    }

    fun toggleShuffle() {
        _state.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        _state.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                },
            )
        }
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val playlist = DesktopPlaylist(UUID.randomUUID().toString(), cleanName)
        val playlists = _state.value.playlists + playlist
        _state.update { it.copy(playlists = playlists) }
        persist { copy(playlists = playlists) }
    }

    fun addToPlaylist(playlistId: String, song: DesktopSong) {
        val playlists = _state.value.playlists.map { playlist ->
            if (playlist.id == playlistId && song.path !in playlist.songPaths) {
                playlist.copy(
                    songPaths = playlist.songPaths + song.path,
                    songs = playlist.songs + song,
                )
            } else {
                playlist
            }
        }
        _state.update { it.copy(playlists = playlists) }
        persist { copy(playlists = playlists) }
    }

    fun songsForPlaylist(playlist: DesktopPlaylist): List<DesktopSong> {
        val knownSongs = buildList {
            addAll(_state.value.songs)
            addAll(_state.value.favoriteSongs)
            addAll(_state.value.listeningHistory.map(ListeningHistoryEntry::song))
            addAll(_state.value.onlineResults)
            addAll(_state.value.recommendedSongs)
            _state.value.homeShelves.values.forEach(::addAll)
        }.associateBy(DesktopSong::path)
        return (playlist.songs + playlist.songPaths.mapNotNull(knownSongs::get))
            .distinctBy(DesktopSong::path)
    }

    private fun recordPlay(song: DesktopSong) {
        val previous = preferences.listeningHistory.firstOrNull { it.song.id == song.id }
        val entry = ListeningHistoryEntry(
            song = song,
            playedAt = System.currentTimeMillis(),
            playCount = (previous?.playCount ?: 0) + 1,
        )
        val history = (listOf(entry) + preferences.listeningHistory.filterNot { it.song.id == song.id })
            .take(100)
        preferences = preferences.copy(listeningHistory = history)
        _state.update { it.copy(listeningHistory = history) }
        persistNow()
    }

    private fun rankRecommendations(seed: DesktopSong, candidates: List<DesktopSong>): List<DesktopSong> {
        val current = _state.value
        val recentPaths = current.listeningHistory.take(12).mapTo(mutableSetOf()) { it.song.path }
        val recentArtists = current.listeningHistory.take(4).map { it.song.displayArtist.lowercase() }
        val queuedPaths = current.queue.mapTo(mutableSetOf(), DesktopSong::path)
        val undesirable = listOf("karaoke", "reaction", "tutorial", "cover")
        return candidates
            .asSequence()
            .filter { it.durationMs == 0L || it.durationMs in 60_000L..1_200_000L }
            .distinctBy(DesktopSong::path)
            .mapIndexed { index, candidate ->
                var score = 220 - index * 3
                if (candidate.displayArtist.equals(seed.displayArtist, ignoreCase = true)) score += 16
                if (candidate.displayArtist.lowercase() in recentArtists) score -= 12
                if (candidate.durationMs in 120_000L..480_000L) score += 8
                if (undesirable.any { it in candidate.title.lowercase() }) score -= 35
                if (candidate.path in recentPaths) score -= 500
                if (candidate.path in queuedPaths || candidate.path == seed.path) score -= 500
                score += kotlin.math.abs(candidate.path.hashCode() % 7)
                candidate to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (song, _) -> song }
            .take(18)
            .toList()
    }

    private fun restoreLastSong(songs: List<DesktopSong>) {
        if (_state.value.player.currentSong != null) return
        val song = songs.firstOrNull { it.path == preferences.lastSongPath } ?: return
        _state.update {
            it.copy(
                queue = songs,
                currentQueueIndex = songs.indexOf(song),
                player = it.player.copy(currentSong = song, durationMs = song.durationMs),
            )
        }
    }

    private fun persist(update: DesktopPreferences.() -> DesktopPreferences) {
        preferences = preferences.update()
        persistNow()
    }

    private fun persistNow() {
        val snapshot = preferences
        scope.launch(Dispatchers.IO) { runCatching { preferencesStore.save(snapshot) } }
    }

    private fun initialFolders(saved: List<String>): List<String> {
        if (saved.isNotEmpty()) return saved
        val music = Path.of(System.getProperty("user.home"), "Music")
        return if (Files.isDirectory(music)) listOf(music.toString()) else emptyList()
    }

    override fun close() {
        onlineSearchJob?.cancel()
        mprisService.close()
        playerEngine.close()
        scope.cancel()
    }
}

private fun Long?.orZero(): Long = this ?: 0L
