package com.theveloper.pixelplay.bot

import dev.arbjerg.lavalink.client.LavalinkClient
import dev.arbjerg.lavalink.client.NodeOptions
import dev.arbjerg.lavalink.client.getUserIdFromToken
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

fun main() {
    acquireInstanceLock().use {
        runBot()
    }
}

private fun runBot() {
    val config = BotConfig.load()
    val lavalink = LavalinkClient(getUserIdFromToken(config.discordToken))
    val music = GuildMusicManager(lavalink, config.idleMinutes)
    lavalink.addNode(
        NodeOptions.Builder()
            .setName("pixelplayer-primary")
            .setServerUri(config.lavalinkUri)
            .setPassword(config.lavalinkPassword)
            .build()
    )

    val jda = JDABuilder.createDefault(config.discordToken)
        .setVoiceDispatchInterceptor(JDAVoiceUpdateListener(lavalink))
        .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setActivity(Activity.listening("/play"))
        .addEventListeners(MusicCommandListener(music))
        .build()
        .awaitReady()

    music.attachJda(jda)
    registerCommands(jda, config)
    println("PixelPlayer Bot is online as ${jda.selfUser.name}.")

    Runtime.getRuntime().addShutdownHook(Thread {
        music.close()
        lavalink.close()
        jda.shutdown()
    })

    jda.awaitShutdown()
}

private fun acquireInstanceLock(): BotInstanceLock {
    val path = Path.of(System.getProperty("java.io.tmpdir"), "pixelplayer-discord-bot.lock")
    val channel = FileChannel.open(path, CREATE, WRITE)
    val lock = channel.tryLock()
    if (lock == null) {
        channel.close()
        error("PixelPlayer Bot is already running. Stop the existing process before starting another one.")
    }
    return BotInstanceLock(channel, lock)
}

private class BotInstanceLock(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }
}

private fun registerCommands(jda: net.dv8tion.jda.api.JDA, config: BotConfig) {
    val target = config.developmentGuildId?.let { guildId -> jda.getGuildById(guildId) }
    if (config.developmentGuildId != null && target == null) {
        error("DISCORD_GUILD_ID=${config.developmentGuildId} is not a server this bot can access.")
    }

    val commands = musicCommands()
    if (target != null) {
        target.updateCommands().addCommands(commands).queue()
        println("Registered development commands in ${target.name}.")
    } else {
        jda.updateCommands().addCommands(commands).queue()
        println("Registered global commands.")
    }
}

private fun musicCommands(): List<CommandData> = listOf(
    Commands.slash("play", "Play a URL or search YouTube")
        .addOption(OptionType.STRING, "query", "Song name, YouTube URL, or playlist URL", true),
    Commands.slash("pause", "Pause the current song"),
    Commands.slash("resume", "Resume the current song"),
    Commands.slash("skip", "Skip to the next song"),
    Commands.slash("stop", "Stop playback and clear the queue"),
    Commands.slash("queue", "Show the current queue"),
    Commands.slash("nowplaying", "Show the current song"),
    Commands.slash("volume", "Set playback volume")
        .addOption(OptionType.INTEGER, "percent", "Volume from 0 to 150", true),
    Commands.slash("autoplay", "View or change automatic related-song playback")
        .addOption(OptionType.BOOLEAN, "enabled", "Turn related-song autoplay on or off", false),
    Commands.slash("leave", "Disconnect PixelPlayer from voice"),
)
