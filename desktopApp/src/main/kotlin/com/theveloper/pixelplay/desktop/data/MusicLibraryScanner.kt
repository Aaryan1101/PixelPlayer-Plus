package com.theveloper.pixelplay.desktop.data

import com.theveloper.pixelplay.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

class MusicLibraryScanner {
    private val supportedExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aiff", "wma",
    )

    suspend fun scan(folders: List<Path>): List<DesktopSong> = withContext(Dispatchers.IO) {
        folders.asSequence()
            .filter(Files::isDirectory)
            .flatMap { folder ->
                Files.walk(folder).use { stream -> stream.toList().asSequence() }
            }
            .filter(Path::isRegularFile)
            .filter { it.extension.lowercase() in supportedExtensions }
            .distinct()
            .map(::readSong)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .toList()
    }

    private fun readSong(path: Path): DesktopSong {
        val fallbackTitle = path.nameWithoutExtension
        return runCatching {
            val audioFile = AudioFileIO.read(path.toFile())
            val tag = audioFile.tag
            DesktopSong(
                path = path.toAbsolutePath().normalize().toString(),
                title = tag?.getFirst(FieldKey.TITLE)?.ifBlank { fallbackTitle } ?: fallbackTitle,
                artist = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
                album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
                durationMs = audioFile.audioHeader.trackLength.toLong() * 1_000L,
                trackNumber = tag?.getFirst(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull() ?: 0,
                year = tag?.getFirst(FieldKey.YEAR)?.take(4)?.toIntOrNull() ?: 0,
            )
        }.getOrElse {
            DesktopSong(
                path = path.toAbsolutePath().normalize().toString(),
                title = fallbackTitle,
                artist = path.parent?.fileName?.toString().orEmpty(),
                album = "",
            )
        }
    }
}
