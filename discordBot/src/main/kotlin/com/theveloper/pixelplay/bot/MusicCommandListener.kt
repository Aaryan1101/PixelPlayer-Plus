package com.theveloper.pixelplay.bot

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class MusicCommandListener(private val music: GuildMusicManager) : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (!event.isFromGuild) {
            event.reply("Music commands can only be used inside a server.").setEphemeral(true).queue()
            return
        }

        when (event.name) {
            "play" -> play(event)
            "pause" -> control(event, "Paused.") { guildId, done -> music.pause(guildId, true, done) }
            "resume" -> control(event, "Resumed.") { guildId, done -> music.pause(guildId, false, done) }
            "skip" -> withControlAccess(event) {
                event.deferReply().queue { hook ->
                    music.skip(event.guild!!.idLong) { result ->
                        val message = when {
                            result.next != null -> "Skipped. Now playing ${result.next.track.displayLine()}."
                            result.skipped -> "Skipped, but no next song was found."
                            else -> "Nothing is playing."
                        }
                        hook.editOriginal(message).queue()
                    }
                }
            }
            "stop" -> withControlAccess(event) {
                val stopped = music.stop(event.guild!!.idLong)
                event.reply(if (stopped) "Playback stopped and the queue was cleared." else "Nothing is playing.").queue()
            }
            "queue" -> showQueue(event)
            "nowplaying" -> showNowPlaying(event)
            "volume" -> volume(event)
            "autoplay" -> autoplay(event)
            "leave" -> withControlAccess(event) {
                music.leave(event.guild!!.idLong)
                event.reply("Disconnected and cleared this server's queue.").queue()
            }
        }
    }

    private fun play(event: SlashCommandInteractionEvent) {
        val channel = voiceChannelFor(event) ?: return
        val guild = event.guild!!
        val botChannel = guild.selfMember.voiceState?.channel
        if (botChannel != null && botChannel.idLong != channel.idLong) {
            event.reply("Join **${botChannel.name}** to control PixelPlayer.").setEphemeral(true).queue()
            return
        }
        if (!guild.selfMember.hasPermission(channel, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)) {
            event.reply("I need **Connect** and **Speak** permission in ${channel.asMention}.").setEphemeral(true).queue()
            return
        }

        val query = event.getOption("query")!!.asString.trim()
        if (query.isEmpty()) {
            event.reply("Enter a song name or URL.").setEphemeral(true).queue()
            return
        }
        event.deferReply().queue { hook ->
            try {
                event.jda.directAudioController.connect(channel)
                music.loadAndEnqueue(guild.idLong, query, event.member!!.effectiveName) { result ->
                    hook.editOriginal(result.message).queue()
                }
            } catch (error: RuntimeException) {
                hook.editOriginal("I could not join ${channel.asMention}: ${error.message ?: "voice connection failed"}").queue()
            }
        }
    }

    private fun control(
        event: SlashCommandInteractionEvent,
        successMessage: String,
        action: (Long, (Boolean) -> Unit) -> Unit,
    ) = withControlAccess(event) {
        event.deferReply().queue { hook ->
            action(event.guild!!.idLong) { success ->
                hook.editOriginal(if (success) successMessage else "Nothing is playing.").queue()
            }
        }
    }

    private fun volume(event: SlashCommandInteractionEvent) = withControlAccess(event) {
        val value = event.getOption("percent")!!.asInt
        if (value !in 0..150) {
            event.reply("Volume must be between 0 and 150.").setEphemeral(true).queue()
            return@withControlAccess
        }
        event.deferReply().queue { hook ->
            music.setVolume(event.guild!!.idLong, value) { success ->
                hook.editOriginal(if (success) "Volume set to **$value%**." else "Could not update the volume.").queue()
            }
        }
    }

    private fun showQueue(event: SlashCommandInteractionEvent) {
        val guildId = event.guild!!.idLong
        val current = music.nowPlaying(guildId)
        val queued = music.queue(guildId)
        if (current == null && queued.isEmpty()) {
            event.reply("The queue is empty.").queue()
            return
        }
        val lines = buildList {
            current?.let { add("**Now:** ${it.track.displayLine()} · requested by ${it.requestedBy.safeDiscord()}") }
            queued.forEachIndexed { index, item ->
                add("`${index + 1}.` ${item.track.displayLine()} · ${item.requestedBy.safeDiscord()}")
            }
        }
        event.reply(lines.joinToString("\n").take(1_950)).queue()
    }

    private fun showNowPlaying(event: SlashCommandInteractionEvent) {
        val item = music.nowPlaying(event.guild!!.idLong)
        event.reply(item?.let { "Now playing ${it.track.displayLine()} · requested by ${it.requestedBy.safeDiscord()}" } ?: "Nothing is playing.").queue()
    }

    private fun autoplay(event: SlashCommandInteractionEvent) {
        val guildId = event.guild!!.idLong
        val option = event.getOption("enabled")
        if (option == null) {
            val status = if (music.isAutoplayEnabled(guildId)) "enabled" else "disabled"
            event.reply("Autoplay is currently **$status** for this server.").queue()
            return
        }

        val enabled = option.asBoolean
        music.setAutoplay(guildId, enabled)
        event.reply(
            if (enabled) "Autoplay enabled. Related music will continue when the queue ends."
            else "Autoplay disabled. Playback will stop when the queue ends."
        ).queue()
    }

    private inline fun withControlAccess(event: SlashCommandInteractionEvent, action: () -> Unit) {
        val memberChannel = voiceChannelFor(event) ?: return
        val botChannel = event.guild!!.selfMember.voiceState?.channel
        if (botChannel != null && botChannel.idLong != memberChannel.idLong) {
            event.reply("Join **${botChannel.name}** to control PixelPlayer.").setEphemeral(true).queue()
            return
        }
        action()
    }

    private fun voiceChannelFor(event: SlashCommandInteractionEvent): AudioChannel? {
        val channel = event.member?.voiceState?.channel
        if (channel == null) {
            event.reply("Join a voice channel first.").setEphemeral(true).queue()
        }
        return channel
    }
}
