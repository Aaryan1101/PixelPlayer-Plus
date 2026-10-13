package com.theveloper.pixelplay.bot

import java.nio.file.Files
import java.nio.file.Path

data class BotConfig(
    val discordToken: String,
    val developmentGuildId: Long?,
    val lavalinkUri: String,
    val lavalinkPassword: String,
    val idleMinutes: Long,
) {
    companion object {
        fun load(environment: Map<String, String> = System.getenv()): BotConfig {
            val fileValues = loadDotEnv()
            fun value(name: String): String? = environment[name]?.takeIf(String::isNotBlank)
                ?: fileValues[name]?.takeIf(String::isNotBlank)

            val token = requireNotNull(value("DISCORD_BOT_TOKEN")) {
                "DISCORD_BOT_TOKEN is missing. Export it or create discordBot/.env."
            }
            require(token != "replace-with-your-discord-bot-token") {
                "Replace the example DISCORD_BOT_TOKEN before starting the bot."
            }

            val password = requireNotNull(value("LAVALINK_PASSWORD")) {
                "LAVALINK_PASSWORD is missing. Export it or create discordBot/.env."
            }
            require(password != "replace-with-a-long-random-password") {
                "Replace the example LAVALINK_PASSWORD before starting the bot."
            }

            return BotConfig(
                discordToken = token,
                developmentGuildId = value("DISCORD_GUILD_ID")?.toLongOrNull(),
                lavalinkUri = value("LAVALINK_URI") ?: "ws://localhost:2333",
                lavalinkPassword = password,
                idleMinutes = (value("BOT_IDLE_MINUTES")?.toLongOrNull() ?: 5L).coerceIn(1L, 60L),
            )
        }

        private fun loadDotEnv(): Map<String, String> {
            val path = sequenceOf(Path.of("discordBot/.env"), Path.of(".env"))
                .firstOrNull(Files::isRegularFile)
                ?: return emptyMap()

            return Files.readAllLines(path).mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#') || '=' !in line) return@mapNotNull null
                val (key, rawValue) = line.split('=', limit = 2)
                key.trim().takeIf(String::isNotEmpty)?.let {
                    it to rawValue.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }.toMap()
        }
    }
}
