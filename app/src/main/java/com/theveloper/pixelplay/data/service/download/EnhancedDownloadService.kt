package com.theveloper.pixelplay.data.service.download

import android.content.Context
import android.os.Environment
import com.theveloper.pixelplay.data.model.EnhancedDownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.DownloadPreferences
import com.theveloper.pixelplay.data.network.youtube.YouTubeExtractorService
import com.theveloper.pixelplay.di.FastOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeExtractorService: YouTubeExtractorService,
    @FastOkHttpClient private val okHttpClient: OkHttpClient,
    private val downloadPreferences: DownloadPreferences
) {

    private val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "downloads")

    init {
        // Create download directory if it doesn't exist
        Timber.d("Enhanced download directory path: ${downloadDir.absolutePath}")
        if (!downloadDir.exists()) {
            val created = downloadDir.mkdirs()
            Timber.d("Enhanced download directory created: $created")
        }
    }

    /**
     * Download YouTube audio with enhanced progress tracking
     */
    fun downloadYouTubeAudio(song: Song): Flow<EnhancedDownloadState> = channelFlow {
        try {
            // Check if already downloaded
            if (isSongDownloaded(song)) {
                val completedState = EnhancedDownloadState(
                    songId = song.id,
                    isDownloading = false,
                    progress = 1f,
                    isComplete = true,
                    fileSize = getDownloadedFile(song)?.length() ?: 0L,
                    downloadedSize = getDownloadedFile(song)?.length() ?: 0L
                )
                downloadPreferences.saveDownloadState(completedState)
                send(completedState)
                return@channelFlow
            }

            // Get existing state or create new one
            val existingState = downloadPreferences.getDownloadState(song.id)
            val initialState = existingState?.copy(
                isDownloading = true,
                error = null,
                lastUpdated = System.currentTimeMillis()
            ) ?: EnhancedDownloadState(
                songId = song.id,
                isDownloading = true,
                progress = 0f,
                lastUpdated = System.currentTimeMillis()
            )

            send(initialState)
            downloadPreferences.saveDownloadState(initialState)

            // Get stream URL
            val videoId = com.theveloper.pixelplay.data.network.youtube.YouTubeToSongMapper.extractVideoId(song.id)
            if (videoId == null) {
                val errorState = initialState.copy(
                    isDownloading = false,
                    error = "Invalid YouTube video ID",
                    lastUpdated = System.currentTimeMillis()
                )
                downloadPreferences.saveDownloadState(errorState)
                send(errorState)
                return@channelFlow
            }

            val streamResult = youTubeExtractorService.getStreamUrl(videoId)
            if (streamResult.isFailure) {
                val error = streamResult.exceptionOrNull()?.message ?: "Failed to get stream URL"
                val errorState = initialState.copy(
                    isDownloading = false,
                    error = error,
                    lastUpdated = System.currentTimeMillis()
                )
                downloadPreferences.saveDownloadState(errorState)
                send(errorState)
                return@channelFlow
            }

            val streamUrl = streamResult.getOrThrow()
            
            // Download with enhanced progress tracking
            withContext(Dispatchers.IO) {
                Timber.d("Starting enhanced download for: ${song.title}")
                
                val request = Request.Builder().url(streamUrl).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw Exception("Response body is null")
                val contentLength = body.contentLength()
                
                Timber.d("HTTP Response: ${response.code}, Content-Length: $contentLength")
                
                // Determine content type and file extension
                val contentType = response.header("Content-Type") ?: "audio/m4a"
                val fileExtension = when {
                    contentType.contains("webm") -> ".webm"
                    contentType.contains("mp4") || contentType.contains("m4a") -> ".m4a"
                    contentType.contains("ogg") -> ".ogg"
                    else -> ".m4a"
                }
                
                val filename = sanitizeFilename("${song.title} - ${song.artist}$fileExtension")
                val file = File(downloadDir, filename)
                
                Timber.d("Downloading to: ${file.absolutePath}")
                
                var downloaded = 0L
                val startTime = System.currentTimeMillis()
                val lastProgressUpdate = AtomicLong(startTime)
                var lastDownloadedAmount = 0L
                
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            // Calculate progress
                            val progress = if (contentLength > 0) {
                                downloaded.toFloat() / contentLength.toFloat()
                            } else {
                                // Fallback: estimate progress based on typical file sizes
                                when {
                                    downloaded < 1_000_000 -> 0.1f // < 1MB
                                    downloaded < 5_000_000 -> 0.3f // < 5MB
                                    downloaded < 10_000_000 -> 0.6f // < 10MB
                                    downloaded < 20_000_000 -> 0.8f // < 20MB
                                    else -> 0.9f // > 20MB
                                }
                            }

                            // Calculate download speed
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastProgressUpdate.get()
                            
                            if (timeDiff > 500) { // Update every 500ms
                                val timeDiffSeconds = timeDiff / 1000.0
                                val downloadSpeed = if (timeDiffSeconds > 0) {
                                    ((downloaded - lastDownloadedAmount) / timeDiffSeconds).toLong()
                                } else 0L
                                
                                val timeRemaining = if (downloadSpeed > 0 && contentLength > 0) {
                                    ((contentLength - downloaded) / downloadSpeed * 1000).toLong()
                                } else 0L

                                val currentState = EnhancedDownloadState(
                                    songId = song.id,
                                    isDownloading = true,
                                    progress = progress,
                                    fileSize = contentLength,
                                    downloadedSize = downloaded,
                                    downloadSpeed = downloadSpeed,
                                    timeRemaining = timeRemaining,
                                    lastUpdated = currentTime
                                )
                                
                                send(currentState)
                                downloadPreferences.saveDownloadState(currentState)
                                
                                lastProgressUpdate.set(currentTime)
                                lastDownloadedAmount = downloaded
                            }
                        }
                    }
                }
                
                // Final completion state
                val completedState = EnhancedDownloadState(
                    songId = song.id,
                    isDownloading = false,
                    progress = 1f,
                    isComplete = true,
                    fileSize = file.length(),
                    downloadedSize = file.length(),
                    downloadSpeed = 0L,
                    timeRemaining = 0L,
                    lastUpdated = System.currentTimeMillis()
                )
                
                // Store the MIME type as a file property
                val mimeTypeFile = File(file.parent, "${file.nameWithoutExtension}.mime")
                mimeTypeFile.writeText(contentType)
                
                downloadPreferences.saveDownloadState(completedState)
                send(completedState)
                
                Timber.d("Enhanced download completed - File size: ${file.length()} bytes")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Enhanced download failed for song: ${song.title}")
            val errorState = EnhancedDownloadState(
                songId = song.id,
                isDownloading = false,
                progress = 0f,
                error = e.message,
                lastUpdated = System.currentTimeMillis()
            )
            downloadPreferences.saveDownloadState(errorState)
            send(errorState)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Retry a failed download
     */
    fun retryDownload(song: Song): Flow<EnhancedDownloadState> {
        val currentState = downloadPreferences.getDownloadState(song.id)
        val retryState = currentState?.copy(
            isDownloading = true,
            error = null,
            retryCount = currentState.retryCount + 1,
            progress = 0f,
            lastUpdated = System.currentTimeMillis()
        ) ?: EnhancedDownloadState(
            songId = song.id,
            isDownloading = true,
            retryCount = 1,
            lastUpdated = System.currentTimeMillis()
        )
        
        downloadPreferences.saveDownloadState(retryState)
        return downloadYouTubeAudio(song)
    }

    /**
     * Check if a song is already downloaded
     */
    fun isSongDownloaded(song: Song): Boolean {
        val baseFilename = sanitizeFilename("${song.title} - ${song.artist}")
        return downloadDir.listFiles()?.any { 
            it.name.startsWith(baseFilename) && it.isFile 
        } ?: false
    }

    /**
     * Get downloaded file for a song
     */
    fun getDownloadedFile(song: Song): File? {
        val baseFilename = sanitizeFilename("${song.title} - ${song.artist}")
        return downloadDir.listFiles()?.find { 
            it.name.startsWith(baseFilename) && it.isFile 
        }
    }

    /**
     * Get the actual MIME type for a downloaded file
     */
    fun getDownloadedFileMimeType(song: Song): String {
        val downloadedFile = getDownloadedFile(song)
        
        if (downloadedFile != null) {
            // Try to read the stored MIME type first
            val mimeTypeFile = File(downloadedFile.parent, "${downloadedFile.nameWithoutExtension}.mime")
            if (mimeTypeFile.exists()) {
                return mimeTypeFile.readText().trim()
            }
            
            // Fallback to extension-based detection
            return when {
                downloadedFile.name.endsWith(".webm") -> "audio/webm"
                downloadedFile.name.endsWith(".m4a") -> "audio/mp4"
                downloadedFile.name.endsWith(".ogg") -> "audio/ogg"
                else -> "audio/m4a"
            }
        }
        
        return "audio/m4a"
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
