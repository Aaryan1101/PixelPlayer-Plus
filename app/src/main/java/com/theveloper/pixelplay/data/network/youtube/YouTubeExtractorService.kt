package com.theveloper.pixelplay.data.network.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractorService @Inject constructor() {

    private val youtubeService = ServiceList.YouTube
    private var lastReinitTime = 0L
    private val REINIT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    private val ytDlpService = YtDlpService()

    /**
     * Re-initialize NewPipe if needed (to handle rate limiting/blocks)
     */
    private suspend fun reinitializeNewPipeIfNeeded() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastReinitTime > REINIT_INTERVAL_MS) {
            try {
                Timber.d("YouTubeExtractor: Re-initializing NewPipe...")
                val downloader = OkHttpDownloader.getInstance()
                NewPipe.init(downloader)
                lastReinitTime = currentTime
                Timber.d("YouTubeExtractor: NewPipe re-initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "YouTubeExtractor: Failed to re-initialize NewPipe")
            }
        }
    }

    /**
     * Search for songs on YouTube
     * @param query Search query
     * @return List of video items matching search
     */
    suspend fun searchSongs(query: String): Result<List<StreamInfoItem>> = withContext(Dispatchers.IO) {
        try {
            val musicQuery = YouTubeToSongMapper.musicSearchQuery(query)
            val items = searchYouTubeMusic(musicQuery)
                .ifEmpty {
                    Timber.d("YouTube Music returned no songs for query: $musicQuery, falling back to YouTube")
                    searchYouTubeVideos(musicQuery)
                }

            Timber.d("Found ${items.size} songs for query: $query")
            Result.success(items)
        } catch (e: Exception) {
            Timber.e(e, "Error searching songs: $query")
            Result.failure(e)
        }
    }

    private fun searchYouTubeMusic(query: String): List<StreamInfoItem> {
        val filters = listOf(
            YoutubeSearchQueryHandlerFactory.MUSIC_SONGS,
            YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
        )

        return filters.firstNotNullOfOrNull { filter ->
            runCatching {
                val searchExtractor = youtubeService.getSearchExtractor(query, listOf(filter), "")
                searchExtractor.fetchPage()
                searchExtractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter(::isPlayableStream)
                    .filter(YouTubeToSongMapper::isLikelyMusicContent)
            }.onFailure { error ->
                Timber.w(error, "YouTube Music search failed for filter: $filter")
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    private fun searchYouTubeVideos(query: String): List<StreamInfoItem> {
        val searchExtractor = youtubeService.getSearchExtractor(query)
        searchExtractor.fetchPage()

        return searchExtractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter(::isPlayableStream)
            .filter(YouTubeToSongMapper::isLikelyMusicContent)
    }

    private fun isPlayableStream(item: StreamInfoItem): Boolean {
        return item.streamType == StreamType.VIDEO_STREAM ||
            item.streamType == StreamType.AUDIO_STREAM
    }

    /**
     * Get next page of search results
     * @param page Page object from previous search
     * @return List of video items from next page
     */
    suspend fun getNextPage(searchExtractor: SearchExtractor, page: Page): Result<List<StreamInfoItem>> = 
        withContext(Dispatchers.IO) {
            try {
                val nextPage = searchExtractor.getPage(page)
                val items = nextPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter(::isPlayableStream)
                    .filter(YouTubeToSongMapper::isLikelyMusicContent)
                
                Result.success(items)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching next page")
                Result.failure(e)
            }
        }

    /**
 * Get stream information for a YouTube video with fallback
 */
suspend fun getStreamInfo(videoUrl: String): Result<StreamExtractor> = withContext(Dispatchers.IO) {
    try {
        // Ensure URL is in correct format
        val fullUrl = if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            "https://www.youtube.com/watch?v=$videoUrl"
        }
        
        Timber.d("YouTubeExtractor: Fetching stream info for: $fullUrl")
        
        val streamExtractor = youtubeService.getStreamExtractor(fullUrl)
        
        try {
            streamExtractor.fetchPage()
            Timber.d("YouTubeExtractor: Got stream info for: ${streamExtractor.name}, Audio streams: ${streamExtractor.audioStreams.size}")
            Result.success(streamExtractor)
        } catch (e: Exception) {
            Timber.w("YouTubeExtractor: Primary extraction failed: ${e.message}")
            
            // Try alternative approach: use different streaming data extraction
            try {
                // Force re-extraction with different method
                val altExtractor = youtubeService.getStreamExtractor(fullUrl)
                altExtractor.fetchPage()
                
                val audioCount = altExtractor.audioStreams.size
                Timber.d("YouTubeExtractor: Alternative extraction found $audioCount audio streams")
                
                if (audioCount > 0) {
                    Result.success(altExtractor)
                } else {
                    throw Exception("No audio streams found with alternative method")
                }
            } catch (altException: Exception) {
                Timber.e("YouTubeExtractor: Alternative extraction also failed")
                throw e // Throw original exception
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "YouTubeExtractor: Error getting stream info: $videoUrl")
        Result.failure(e)
    }
}

    /**
     * Get direct audio stream URL for a YouTube video with multiple fallbacks
     * @param videoUrl YouTube video URL or ID
     * @return Direct audio stream URL
     */
    suspend fun getStreamUrl(videoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastError: Exception? = null
        
        // Try NewPipe first with retries
        repeat(maxRetries) { attempt ->
            try {
                Timber.d("YouTubeExtractor: Getting stream URL for: $videoUrl (attempt ${attempt + 1}/$maxRetries)")
                
                // Re-initialize NewPipe if needed
                reinitializeNewPipeIfNeeded()
                
                val streamInfoResult = getStreamInfo(videoUrl)
                
                if (streamInfoResult.isFailure) {
                    val error = streamInfoResult.exceptionOrNull() as? Exception
                    lastError = error ?: Exception("Failed to get stream info")
                    Timber.w("YouTubeExtractor: Attempt ${attempt + 1} failed", lastError)
                    
                    // Wait before retry (exponential backoff)
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (attempt + 1)) // 1s, 2s, 3s delays
                    }
                    return@repeat
                }
                
                val streamExtractor = streamInfoResult.getOrThrow()
                
                // Get the best audio stream (prioritize full-length over bitrate)
                val audioStreams = streamExtractor.audioStreams
                Timber.d("YouTubeExtractor: Found ${audioStreams.size} audio streams")
                
                // Filter for audio streams and prioritize full-length content
                val validAudioStreams = audioStreams
                    .filter { it.content != null && it.content.isNotEmpty() }
                
                // Log all available streams for debugging
                Timber.d("YouTubeExtractor: Available audio streams:")
                validAudioStreams.forEachIndexed { index, stream ->
                    Timber.d("YouTubeExtractor: Stream $index - bitrate: ${stream.averageBitrate}, format: ${stream.format}, itag: ${stream.itag}")
                }
                
                // Try to get full-length audio stream (avoid previews/short clips)
                val audioStream = try {
                    // Prioritize medium bitrate streams (likely full songs) over very high bitrate (likely previews)
                    val mediumBitrateStreams = validAudioStreams.filter { 
                        it.averageBitrate != null && it.averageBitrate!! in 50..300 
                    }
                    val highBitrateStreams = validAudioStreams.filter { 
                        it.averageBitrate != null && it.averageBitrate!! > 300 
                    }
                    val lowBitrateStreams = validAudioStreams.filter { 
                        it.averageBitrate == null || it.averageBitrate!! < 50 
                    }
                    
                    // Try medium bitrate first (likely full songs), then low, then high (previews)
                    mediumBitrateStreams.maxByOrNull { it.averageBitrate ?: 0 }
                        ?: lowBitrateStreams.maxByOrNull { it.averageBitrate ?: 0 }
                        ?: highBitrateStreams.maxByOrNull { it.averageBitrate ?: 0 }
                } catch (e: Exception) {
                    // Fallback to any stream if selection fails
                    validAudioStreams.maxByOrNull { it.averageBitrate ?: 0 }
                }
                
                if (audioStream != null && audioStream.content.isNotEmpty()) {
                    val streamUrl = audioStream.content
                    Timber.d("YouTubeExtractor: Got audio stream URL: ${streamUrl.take(100)}..., bitrate: ${audioStream.averageBitrate}, format: ${audioStream.format}")
                    return@withContext Result.success(streamUrl)
                } else {
                    // Log details of all streams for debugging
                    Timber.e("YouTubeExtractor: No valid audio stream available. Total streams: ${audioStreams.size}")
                    audioStreams.forEachIndexed { index, stream ->
                        Timber.d("YouTubeExtractor: Stream $index - content: ${stream.content?.take(50)}, bitrate: ${stream.averageBitrate}, format: ${stream.format}")
                    }
                    
                    // Try fallback: use any stream with content, even if bitrate is null
                    val fallbackStream = audioStreams.find { it.content != null && it.content.isNotEmpty() }
                    if (fallbackStream != null) {
                        Timber.w("YouTubeExtractor: Using fallback audio stream")
                        return@withContext Result.success(fallbackStream.content)
                    } else {
                        lastError = Exception("No audio stream available")
                        Timber.e("YouTubeExtractor: No audio stream available", lastError)
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Timber.e(e, "YouTubeExtractor: Attempt ${attempt + 1} failed for: $videoUrl")
                
                // Wait before retry (exponential backoff)
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // 1s, 2s, 3s delays
                }
            }
        }
        
        // NewPipe failed, try yt-dlp as fallback
        Timber.w("YouTubeExtractor: All NewPipe attempts failed, trying yt-dlp fallback")
        
        try {
            val ytDlpAvailable = ytDlpService.isYtDlpAvailable()
            if (ytDlpAvailable) {
                val ytDlpResult = ytDlpService.getStreamUrl(videoUrl)
                if (ytDlpResult.isSuccess) {
                    val streamUrl = ytDlpResult.getOrThrow()
                    Timber.d("YouTubeExtractor: yt-dlp fallback succeeded: ${streamUrl.take(100)}...")
                    return@withContext Result.success(streamUrl)
                } else {
                    Timber.w("YouTubeExtractor: yt-dlp fallback failed", ytDlpResult.exceptionOrNull())
                }
            } else {
                Timber.w("YouTubeExtractor: yt-dlp not available for fallback")
            }
        } catch (e: Exception) {
            Timber.e(e, "YouTubeExtractor: Error during yt-dlp fallback")
        }
        
        // All methods failed
        Timber.e("YouTubeExtractor: All extraction methods failed for: $videoUrl", lastError)
        Result.failure(lastError ?: Exception("All extraction methods failed"))
    }

    /**
     * Get related videos for a given video
     * @param videoUrl YouTube video URL or ID
     * @return List of related video items
     */
    suspend fun getRelatedVideos(videoUrl: String): Result<List<StreamInfoItem>> = withContext(Dispatchers.IO) {
        try {
            val streamInfoResult = getStreamInfo(videoUrl)
            
            if (streamInfoResult.isFailure) {
                return@withContext Result.failure(
                    streamInfoResult.exceptionOrNull() ?: Exception("Failed to get stream info")
                )
            }
            
            val streamExtractor = streamInfoResult.getOrThrow()
            val relatedItems = streamExtractor.relatedItems?.items
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter(::isPlayableStream)
                ?.filter(YouTubeToSongMapper::isLikelyMusicContent) ?: emptyList()
            
            Timber.d("Found ${relatedItems.size} related videos")
            Result.success(relatedItems)
        } catch (e: Exception) {
            Timber.e(e, "Error getting related videos: $videoUrl")
            Result.failure(e)
        }
    }
}
