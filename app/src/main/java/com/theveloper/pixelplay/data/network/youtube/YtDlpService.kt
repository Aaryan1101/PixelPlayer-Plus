package com.theveloper.pixelplay.data.network.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Singleton
class YtDlpService @Inject constructor() {

    /**
     * Extract audio stream URL using yt-dlp as fallback
     * @param videoUrl YouTube video URL or ID
     * @return Direct audio stream URL
     */
    suspend fun getStreamUrl(videoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("YtDlp: Attempting to extract stream URL for: $videoUrl")
            
            // Ensure URL is in correct format
            val fullUrl = if (videoUrl.startsWith("http")) {
                videoUrl
            } else {
                "https://www.youtube.com/watch?v=$videoUrl"
            }
            
            // Build yt-dlp command
            val command = arrayOf(
                "yt-dlp",
                "--get-url",
                "--format", "bestaudio[ext=m4a]/bestaudio/best",
                "--no-warnings",
                "--no-call-home",
                "--no-check-certificate",
                fullUrl
            )
            
            Timber.d("YtDlp: Running command: ${command.joinToString(" ")}")
            
            // Execute command
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            // Wait for completion with timeout
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                throw Exception("yt-dlp command timed out")
            }
            
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = BufferedReader(InputStreamReader(process.inputStream))
                    .readText()
                throw Exception("yt-dlp failed with exit code $exitCode: $errorOutput")
            }
            
            // Read the output
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readText()
                .trim()
            
            if (output.isEmpty()) {
                throw Exception("yt-dlp returned empty output")
            }
            
            // yt-dlp might return multiple URLs (for different formats), take the first one
            val streamUrl = output.lines().firstOrNull { it.isNotEmpty() }
                ?: throw Exception("No stream URL found in yt-dlp output")
            
            Timber.d("YtDlp: Successfully extracted stream URL: ${streamUrl.take(100)}...")
            Result.success(streamUrl)
            
        } catch (e: Exception) {
            Timber.e(e, "YtDlp: Failed to extract stream URL for: $videoUrl")
            Result.failure(e)
        }
    }
    
    /**
     * Check if yt-dlp is available on the system
     */
    suspend fun isYtDlpAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("yt-dlp", "--version")
                .redirectErrorStream(true)
                .start()
            
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext false
            }
            
            val exitCode = process.exitValue()
            val available = exitCode == 0
            
            if (available) {
                val version = BufferedReader(InputStreamReader(process.inputStream))
                    .readText()
                    .trim()
                Timber.d("YtDlp: Available - version: $version")
            } else {
                Timber.w("YtDlp: Not available - exit code: $exitCode")
            }
            
            available
        } catch (e: Exception) {
            Timber.e(e, "YtDlp: Error checking availability")
            false
        }
    }
}
