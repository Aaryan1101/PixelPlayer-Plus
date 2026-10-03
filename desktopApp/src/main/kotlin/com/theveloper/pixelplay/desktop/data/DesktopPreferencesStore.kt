package com.theveloper.pixelplay.desktop.data

import com.theveloper.pixelplay.desktop.model.DesktopPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class DesktopPreferencesStore(
    private val configFile: Path = Path.of(
        System.getProperty("user.home"),
        ".config",
        "pixelplayer-desktop",
        "preferences.json",
    ),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun load(): DesktopPreferences {
        if (!configFile.exists()) return DesktopPreferences()
        return runCatching {
            json.decodeFromString<DesktopPreferences>(Files.readString(configFile))
        }.getOrDefault(DesktopPreferences())
    }

    fun save(preferences: DesktopPreferences) {
        Files.createDirectories(configFile.parent)
        val temporary = configFile.resolveSibling("${configFile.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(preferences))
        runCatching {
            Files.move(
                temporary,
                configFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }
}
