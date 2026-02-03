package com.theveloper.pixelplay.data.network.youtube

import android.content.Context
import android.os.Environment
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.theveloper.pixelplay.di.FastOkHttpClient

@Singleton
class YouTubeDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeExtractorService: YouTubeExtractorService,
    @FastOkHttpClient private val okHttpClient: OkHttpClient
) {

    private val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "downloads")

    init {
        // Create download directory if it doesn't exist
        Timber.d("Download directory path: ${downloadDir.absolutePath}")
        if (!downloadDir.exists()) {
            val created = downloadDir.mkdirs()
            Timber.d("Download directory created: $created")
        } else {
            Timber.d("Download directory exists: ${downloadDir.exists()}")
            val files = downloadDir.listFiles()
            Timber.d("Files in download directory: ${files?.size ?: 0}")
            files?.forEach { file ->
                Timber.d("  - ${file.name} (${file.length()} bytes)")
            }
        }
    }

    /**
     * Download YouTube audio with progress tracking
     */
    fun downloadYouTubeAudio(song: Song): Flow<DownloadState> = channelFlow {
        try {
            send(DownloadState(song.id, isDownloading = true, progress = 0f))
            
            // Get stream URL
            val videoId = YouTubeToSongMapper.extractVideoId(song.id)
            if (videoId == null) {
                send(DownloadState(song.id, isDownloading = false, progress = 0f, error = "Invalid YouTube video ID"))
                return@channelFlow
            }

            val streamResult = youTubeExtractorService.getStreamUrl(videoId)
            if (streamResult.isFailure) {
                val error = streamResult.exceptionOrNull()?.message ?: "Failed to get stream URL"
                send(DownloadState(song.id, isDownloading = false, progress = 0f, error = error))
                return@channelFlow
            }

            val streamUrl = streamResult.getOrThrow()
            
            // Download the file with correct extension based on content type
            val filename: String
            val file: File
            val contentType: String
            
            // Download with progress tracking
            withContext(Dispatchers.IO) {
                Timber.d("Starting download for: ${song.title}")
                
                Timber.d("Stream URL extracted: $streamUrl")
                
                val request = Request.Builder().url(streamUrl).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                Timber.d("HTTP Response: ${response.code}, Content-Length: ${response.body?.contentLength()}")
                
                // Determine content type and file extension
                contentType = response.header("Content-Type") ?: "audio/m4a"
                val fileExtension = when {
                    contentType.contains("webm") -> ".webm"
                    contentType.contains("mp4") || contentType.contains("m4a") -> ".m4a"
                    contentType.contains("ogg") -> ".ogg"
                    else -> ".m4a" // fallback
                }
                
                Timber.d("Content type: $contentType, File extension: $fileExtension")
                
                filename = sanitizeFilename("${song.title} - ${song.artist}$fileExtension")
                file = File(downloadDir, filename)
                
                Timber.d("Downloading to: ${file.absolutePath}")
                Timber.d("Download directory writable: ${downloadDir.canWrite()}")
                
                if (response.body == null) {
                    throw Exception("Response body is null")
                }
                
                val body = response.body!!
                val contentLength = body.contentLength()
                var downloaded = 0L

                Timber.d("Starting file write - Content length: $contentLength")

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val progress = if (contentLength > 0) {
                                downloaded.toFloat() / contentLength.toFloat()
                            } else 0f

                            // Send progress from within withContext
                            send(DownloadState(song.id, isDownloading = true, progress = progress))
                        }
                    }
                }
                
                Timber.d("Download completed - File size: ${file.length()} bytes")
                
                // Store the MIME type as a file property for later retrieval
                val mimeTypeFile = File(file.parent, "${file.nameWithoutExtension}.mime")
                mimeTypeFile.writeText(contentType)
                
                Timber.d("MIME type saved to: ${mimeTypeFile.absolutePath}")
                
                send(DownloadState(song.id, isDownloading = false, progress = 1f, isComplete = true))
                
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to download YouTube audio: ${song.title}")
            send(DownloadState(song.id, isDownloading = false, progress = 0f, error = e.message))
        }
    }

    private fun sanitizeFilename(filename: String): String {
        // Remove invalid characters for filenames
        return filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Check if a song is already downloaded
     */
    fun isSongDownloaded(song: Song): Boolean {
        // Try to find the downloaded file with any extension
        val baseFilename = sanitizeFilename("${song.title} - ${song.artist}")
        return downloadDir.listFiles()?.any { 
            it.name.startsWith(baseFilename) && it.isFile 
        } ?: false
    }

    /**
     * Get downloaded file for a song
     */
    fun getDownloadedFile(song: Song): File? {
        // Try to find the downloaded file with any extension
        val baseFilename = sanitizeFilename("${song.title} - ${song.artist}")
        return downloadDir.listFiles()?.find { 
            it.name.startsWith(baseFilename) && it.isFile 
        }
    }

    /**
     * Get the actual MIME type for a downloaded file
     */
    fun getDownloadedFileMimeType(song: Song): String {
        // Try to find the downloaded file with any extension
        val baseFilename = sanitizeFilename("${song.title} - ${song.artist}")
        val downloadedFile = downloadDir.listFiles()?.find { 
            it.name.startsWith(baseFilename) && it.isFile 
        }
        
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
                else -> "audio/mp4" // fallback
            }
        }
        
        return "audio/mp4" // default fallback
    }

    /**
     * Get all downloaded songs
     */
    fun getDownloadedSongs(): List<File> {
        return downloadDir.listFiles()?.filter { 
            it.exists() && it.isFile && (
                it.name.endsWith(".m4a") || 
                it.name.endsWith(".webm") || 
                it.name.endsWith(".ogg")
            )
        } ?: emptyList()
    }
}
