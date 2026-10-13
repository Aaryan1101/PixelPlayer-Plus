package com.theveloper.pixelplay.bot

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.event.TrackEndEvent
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent
import dev.arbjerg.lavalink.client.player.LoadFailed
import dev.arbjerg.lavalink.client.player.PlaylistLoaded
import dev.arbjerg.lavalink.client.player.SearchResult
import dev.arbjerg.lavalink.client.player.Track
import dev.arbjerg.lavalink.client.player.TrackLoaded
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class QueuedTrack(
    val track: Track,
    val requestedBy: String,
    val alternatives: List<Track> = emptyList(),
)

data class EnqueueResult(
    val message: String,
    val accepted: Int = 0,
)

data class SkipResult(
    val skipped: Boolean,
    val next: QueuedTrack? = null,
)

class GuildMusicManager(
    private val lavalink: LavalinkClient,
    private val idleMinutes: Long,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<Long, GuildSession>()
    private val idleExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "pixelplayer-bot-idle").apply { isDaemon = true }
    }
    private lateinit var jda: JDA

    init {
        lavalink.on(TrackEndEvent::class.java).subscribe { event ->
            if (event.endReason.name in ADVANCE_REASONS) {
                advance(event.guildId, event.track, event.endReason.name == "LOAD_FAILED")
            }
        }
        lavalink.on(TrackExceptionEvent::class.java).subscribe { event ->
            logger.warn(
                "Playback failed for ${event.track.info.identifier} in guild ${event.guildId}: " +
                    event.exception.message
            )
        }
    }

    fun attachJda(jda: JDA) {
        this.jda = jda
    }

    fun loadAndEnqueue(
        guildId: Long,
        input: String,
        requestedBy: String,
        callback: (EnqueueResult) -> Unit,
    ) {
        val identifier = if (input.looksLikeUrl()) input else "ytsearch:$input"
        lavalink.getOrCreateLink(guildId).loadItem(identifier).subscribe({ result ->
            when (result) {
                is TrackLoaded -> callback(addTracks(guildId, listOf(result.track), requestedBy, false))
                is SearchResult -> {
                    val candidates = result.tracks.matchingSearchCandidates(input)
                    val track = candidates.firstOrNull()
                    callback(
                        if (track == null) EnqueueResult("No results found for **${input.safeDiscord()}**.")
                        else addTracks(
                            guildId = guildId,
                            tracks = listOf(track),
                            requestedBy = requestedBy,
                            playlist = false,
                            alternatives = candidates.drop(1),
                        )
                    )
                }
                is PlaylistLoaded -> {
                    val tracks = result.tracks.take(MAX_PLAYLIST_TRACKS)
                    callback(
                        if (tracks.isEmpty()) EnqueueResult("That playlist has no playable tracks.")
                        else addTracks(guildId, tracks, requestedBy, true)
                    )
                }
                is LoadFailed -> callback(EnqueueResult("Could not load that audio: ${result.exception.message ?: "unknown Lavalink error"}"))
                else -> callback(EnqueueResult("No playable results were found."))
            }
        }, { error ->
            logger.error("Audio load failed in guild $guildId", error)
            callback(EnqueueResult("The audio service could not load that request. Check that Lavalink is running."))
        })
    }

    fun pause(guildId: Long, paused: Boolean, callback: (Boolean) -> Unit) {
        val current = sessions[guildId]?.current
        if (current == null) {
            callback(false)
            return
        }
        lavalink.getOrCreateLink(guildId).createOrUpdatePlayer().setPaused(paused)
            .subscribe({ callback(true) }, { callback(false) })
    }

    fun skip(guildId: Long, callback: (SkipResult) -> Unit) {
        val session = sessions[guildId]
        if (session == null) {
            callback(SkipResult(skipped = false))
            return
        }
        val skipped = session.current
        val next = synchronized(session) {
            session.current = null
            session.queue.pollFirst().also { session.current = it }
        }
        if (next == null) {
            lavalink.getOrCreateLink(guildId).createOrUpdatePlayer().stopTrack().subscribe()
            if (skipped != null && session.autoplayEnabled) {
                requestAutoplay(guildId, session, skipped.track) { autoplayTrack ->
                    callback(SkipResult(skipped = true, next = autoplayTrack))
                }
            } else {
                scheduleIdleDisconnect(guildId, session)
                callback(SkipResult(skipped = skipped != null))
            }
        } else {
            start(guildId, session, next)
            callback(SkipResult(skipped = skipped != null, next = next))
        }
    }

    fun stop(guildId: Long): Boolean {
        val session = sessions[guildId] ?: return false
        val hadAudio = synchronized(session) {
            val result = session.current != null || session.queue.isNotEmpty()
            session.autoplayGeneration++
            session.current = null
            session.queue.clear()
            result
        }
        lavalink.getOrCreateLink(guildId).createOrUpdatePlayer().stopTrack().subscribe()
        scheduleIdleDisconnect(guildId, session)
        return hadAudio
    }

    fun setVolume(guildId: Long, volume: Int, callback: (Boolean) -> Unit) {
        val session = sessions.computeIfAbsent(guildId) { GuildSession() }
        session.volume = volume
        lavalink.getOrCreateLink(guildId).createOrUpdatePlayer().setVolume(volume)
            .subscribe({ callback(true) }, { callback(false) })
    }

    fun nowPlaying(guildId: Long): QueuedTrack? = sessions[guildId]?.current

    fun isAutoplayEnabled(guildId: Long): Boolean =
        sessions.computeIfAbsent(guildId) { GuildSession() }.autoplayEnabled

    fun setAutoplay(guildId: Long, enabled: Boolean) {
        val session = sessions.computeIfAbsent(guildId) { GuildSession() }
        val seed = synchronized(session) {
            session.autoplayEnabled = enabled
            if (!enabled) session.autoplayGeneration++
            if (enabled && session.current == null && session.queue.isEmpty()) session.lastTrack else null
        }
        if (seed != null) requestAutoplay(guildId, session, seed)
    }

    fun queue(guildId: Long): List<QueuedTrack> = sessions[guildId]?.let { session ->
        synchronized(session) { session.queue.take(QUEUE_PREVIEW_SIZE) }
    }.orEmpty()

    fun leave(guildId: Long) {
        sessions.remove(guildId)?.let { session ->
            synchronized(session) {
                session.autoplayEnabled = false
                session.autoplayGeneration++
                session.idleTask?.cancel(false)
                session.current = null
                session.queue.clear()
            }
        }
        lavalink.getLinkIfCached(guildId)?.destroy()?.subscribe()
        if (::jda.isInitialized) {
            jda.getGuildById(guildId)?.let { guild -> jda.directAudioController.disconnect(guild) }
        }
    }

    private fun addTracks(
        guildId: Long,
        tracks: List<Track>,
        requestedBy: String,
        playlist: Boolean,
        alternatives: List<Track> = emptyList(),
    ): EnqueueResult {
        val session = sessions.computeIfAbsent(guildId) { GuildSession() }
        val requestedItems = tracks.mapIndexed { index, track ->
            QueuedTrack(
                track = track,
                requestedBy = requestedBy,
                alternatives = if (!playlist && index == 0) alternatives else emptyList(),
            )
        }
        var startNow: QueuedTrack? = null
        val items = synchronized(session) {
            val occupied = session.queue.size + if (session.current == null) 0 else 1
            val accepted = requestedItems.take((MAX_QUEUE_SIZE - occupied).coerceAtLeast(0))
            if (accepted.isEmpty()) return@synchronized emptyList()
            session.autoplayGeneration++
            session.idleTask?.cancel(false)
            session.idleTask = null
            if (session.current == null) {
                startNow = accepted.first()
                session.current = startNow
                accepted.drop(1).forEach(session.queue::addLast)
            } else {
                accepted.forEach(session.queue::addLast)
            }
            accepted
        }
        if (items.isEmpty()) return EnqueueResult("This server's queue is full ($MAX_QUEUE_SIZE tracks).")
        startNow?.let { start(guildId, session, it) }

        return when {
            playlist -> {
                val limited = if (items.size < requestedItems.size) " The queue limit was reached." else ""
                EnqueueResult("Added **${items.size} tracks** to the queue.$limited", items.size)
            }
            startNow != null -> EnqueueResult("Loading **${items.first().track.displayTitle()}**.", 1)
            else -> EnqueueResult("Queued **${items.first().track.displayTitle()}**.", 1)
        }
    }

    private fun start(guildId: Long, session: GuildSession, item: QueuedTrack) {
        synchronized(session) {
            session.lastTrack = item.track
            val identifier = item.track.info.identifier
            session.recentTrackIds.remove(identifier)
            session.recentTrackIds.addLast(identifier)
            while (session.recentTrackIds.size > AUTOPLAY_HISTORY_SIZE) {
                session.recentTrackIds.removeFirst()
            }
        }
        lavalink.getOrCreateLink(guildId).createOrUpdatePlayer()
            .setTrack(item.track)
            .setVolume(session.volume)
            .setPaused(false)
            .subscribe(null) { error ->
                logger.error("Could not start a track in guild $guildId", error)
                advance(guildId, item.track, loadFailed = true)
            }
    }

    private fun advance(guildId: Long, finishedTrack: Track, loadFailed: Boolean) {
        val session = sessions[guildId] ?: return
        var retry: QueuedTrack? = null
        val next = synchronized(session) {
            val current = session.current
            if (loadFailed && current?.track?.info?.identifier == finishedTrack.info.identifier &&
                current.alternatives.isNotEmpty()
            ) {
                retry = current.copy(
                    track = current.alternatives.first(),
                    alternatives = current.alternatives.drop(1),
                ).also { session.current = it }
                null
            } else {
                session.current = null
                session.queue.pollFirst().also { session.current = it }
            }
        }
        retry?.let {
            logger.info(
                "Retrying unavailable search result with ${it.track.info.identifier} in guild $guildId " +
                    "(${it.alternatives.size} alternatives remain)"
            )
            start(guildId, session, it)
            return
        }
        when {
            next != null -> start(guildId, session, next)
            loadFailed -> scheduleIdleDisconnect(guildId, session)
            session.autoplayEnabled -> requestAutoplay(guildId, session, finishedTrack)
            else -> scheduleIdleDisconnect(guildId, session)
        }
    }

    private fun requestAutoplay(
        guildId: Long,
        session: GuildSession,
        seed: Track,
        callback: (QueuedTrack?) -> Unit = {},
    ) {
        val generation = synchronized(session) {
            if (!session.autoplayEnabled || session.current != null || session.queue.isNotEmpty()) null
            else ++session.autoplayGeneration
        }
        if (generation == null) {
            callback(null)
            return
        }
        val videoId = seed.info.identifier
        val mixIdentifier = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"

        lavalink.getOrCreateLink(guildId).loadItem(mixIdentifier).subscribe({ result ->
            val tracks = result.autoplayTracks()
            val candidate = synchronized(session) {
                tracks.firstOrNull { it.info.identifier !in session.recentTrackIds }
            }
            if (candidate != null) {
                startAutoplayCandidate(guildId, session, candidate, generation, callback)
            } else {
                requestAutoplayFallback(guildId, session, seed, generation, callback)
            }
        }, { error ->
            logger.warn("YouTube Mix lookup failed in guild $guildId; trying search fallback", error)
            requestAutoplayFallback(guildId, session, seed, generation, callback)
        })
    }

    private fun requestAutoplayFallback(
        guildId: Long,
        session: GuildSession,
        seed: Track,
        generation: Long,
        callback: (QueuedTrack?) -> Unit,
    ) {
        val query = "ytsearch:${seed.info.author} songs like ${seed.info.title}"
        lavalink.getOrCreateLink(guildId).loadItem(query).subscribe({ result ->
            val candidate = synchronized(session) {
                result.autoplayTracks().firstOrNull { it.info.identifier !in session.recentTrackIds }
            }
            if (candidate == null) {
                finishAutoplayAttempt(guildId, session, generation, callback)
            } else {
                startAutoplayCandidate(guildId, session, candidate, generation, callback)
            }
        }, { error ->
            logger.warn("Autoplay fallback failed in guild $guildId", error)
            finishAutoplayAttempt(guildId, session, generation, callback)
        })
    }

    private fun startAutoplayCandidate(
        guildId: Long,
        session: GuildSession,
        track: Track,
        generation: Long,
        callback: (QueuedTrack?) -> Unit,
    ) {
        val item = synchronized(session) {
            if (generation != session.autoplayGeneration || !session.autoplayEnabled ||
                session.current != null || session.queue.isNotEmpty()
            ) null else QueuedTrack(track, AUTOPLAY_REQUESTER).also { session.current = it }
        }
        if (item == null) {
            callback(null)
            return
        }
        start(guildId, session, item)
        callback(item)
    }

    private fun finishAutoplayAttempt(
        guildId: Long,
        session: GuildSession,
        generation: Long,
        callback: (QueuedTrack?) -> Unit,
    ) {
        val shouldDisconnect = synchronized(session) {
            generation == session.autoplayGeneration && session.current == null && session.queue.isEmpty()
        }
        if (shouldDisconnect) scheduleIdleDisconnect(guildId, session)
        callback(null)
    }

    private fun scheduleIdleDisconnect(guildId: Long, session: GuildSession) {
        synchronized(session) {
            session.idleTask?.cancel(false)
            session.idleTask = idleExecutor.schedule({
                val stillIdle = synchronized(session) { session.current == null && session.queue.isEmpty() }
                if (stillIdle) leave(guildId)
            }, idleMinutes, TimeUnit.MINUTES)
        }
    }

    override fun close() {
        idleExecutor.shutdownNow()
        sessions.keys.toList().forEach(::leave)
    }

    private class GuildSession {
        val queue = ArrayDeque<QueuedTrack>()
        val recentTrackIds = ArrayDeque<String>()
        var current: QueuedTrack? = null
        var lastTrack: Track? = null
        var volume: Int = DEFAULT_VOLUME
        var autoplayEnabled: Boolean = true
        var autoplayGeneration: Long = 0
        var idleTask: ScheduledFuture<*>? = null
    }

    companion object {
        private val ADVANCE_REASONS = setOf("FINISHED", "LOAD_FAILED")
        private const val DEFAULT_VOLUME = 80
        private const val MAX_PLAYLIST_TRACKS = 100
        private const val MAX_SEARCH_CANDIDATES = 8
        private const val MAX_QUEUE_SIZE = 500
        private const val QUEUE_PREVIEW_SIZE = 10
        private const val AUTOPLAY_HISTORY_SIZE = 25
        private const val AUTOPLAY_REQUESTER = "PixelPlayer Autoplay"
    }
}

private fun Any.autoplayTracks(): List<Track> = when (this) {
    is TrackLoaded -> listOf(track)
    is PlaylistLoaded -> tracks
    is SearchResult -> tracks
    else -> emptyList()
}

fun Track.displayTitle(): String = info.title.safeDiscord()

fun Track.displayLine(): String = "**${displayTitle()}** — ${info.author.safeDiscord()}"

private fun String.looksLikeUrl(): Boolean = startsWith("http://") || startsWith("https://")

private fun List<Track>.matchingSearchCandidates(query: String): List<Track> {
    val requiredTerms = query.searchTerms()
    if (requiredTerms.isEmpty()) return take(1)

    return asSequence()
        .filter { track ->
            val searchableMetadata = "${track.info.title} ${track.info.author}".normalizedForSearch()
            requiredTerms.all(searchableMetadata::contains)
        }
        .take(8)
        .toList()
        .ifEmpty { take(1) }
}

private fun String.searchTerms(): Set<String> =
    normalizedForSearch()
        .split(' ')
        .asSequence()
        .filter { it.length >= 2 && it !in SEARCH_NOISE_WORDS }
        .toSet()

private fun String.normalizedForSearch(): String =
    lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private val SEARCH_NOISE_WORDS = setOf(
    "audio",
    "full",
    "hd",
    "lyrics",
    "lyrical",
    "music",
    "official",
    "song",
    "video",
)

fun String.safeDiscord(): String = replace("@", "@\u200B").take(200)
