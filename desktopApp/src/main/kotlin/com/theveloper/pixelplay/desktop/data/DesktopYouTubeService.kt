package com.theveloper.pixelplay.desktop.data

import com.theveloper.pixelplay.desktop.model.DesktopSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

class DesktopYouTubeService {
    private val youtube = ServiceList.YouTube

    init {
        NewPipe.init(DesktopDownloader())
    }

    suspend fun search(query: String): Result<List<DesktopSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = youtube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                "",
            )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .distinctBy(StreamInfoItem::getUrl)
                .take(20)
                .map { item ->
                    DesktopSong(
                        path = item.url,
                        title = item.name,
                        artist = item.uploaderName.orEmpty(),
                        album = "Online",
                        durationMs = item.duration.coerceAtLeast(0L) * 1_000L,
                        isOnline = true,
                        artworkUrl = bestArtwork(item),
                    )
                }
        }
    }

    suspend fun resolveAudioUrl(videoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        resolvePlayback(videoUrl).map(OnlinePlayback::audioUrl)
    }

    suspend fun resolvePlayback(videoUrl: String): Result<OnlinePlayback> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = youtube.getStreamExtractor(videoUrl)
            extractor.fetchPage()
            val audioUrl = extractor.audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate ?: 0 }
                ?.content
                ?: error("No playable audio stream was returned")
            val related = extractor.relatedItems?.items.orEmpty()
                .filterIsInstance<StreamInfoItem>()
                .distinctBy(StreamInfoItem::getUrl)
                .take(40)
                .map(::toSong)
            OnlinePlayback(audioUrl, related)
        }
    }

    private fun toSong(item: StreamInfoItem): DesktopSong = DesktopSong(
        path = item.url,
        title = item.name,
        artist = item.uploaderName.orEmpty(),
        album = "Online",
        durationMs = item.duration.coerceAtLeast(0L) * 1_000L,
        isOnline = true,
        artworkUrl = bestArtwork(item),
    )

    private fun bestArtwork(item: StreamInfoItem): String? {
        val videoId = Regex("[?&]v=([^&]+)").find(item.url)?.groupValues?.getOrNull(1)
            ?: Regex("youtu\\.be/([^?&/]+)").find(item.url)?.groupValues?.getOrNull(1)
        if (!videoId.isNullOrBlank()) {
            return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        }
        return item.thumbnails.maxByOrNull { thumbnail ->
            thumbnail.width.coerceAtLeast(0).toLong() * thumbnail.height.coerceAtLeast(0).toLong()
        }?.url
    }
}

data class OnlinePlayback(
    val audioUrl: String,
    val relatedSongs: List<DesktopSong>,
)

private class DesktopDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val body = request.dataToSend()?.let { RequestBody.create(null, it) }
        val builder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        client.newCall(builder.build()).execute().use { response ->
            return ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }
}
