package com.theveloper.pixelplay.data.model

data class EnhancedDownloadState(
    val songId: String,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null,
    val downloadSpeed: Long = 0L, // bytes per second
    val timeRemaining: Long = 0L, // milliseconds
    val fileSize: Long = 0L, // total file size in bytes
    val downloadedSize: Long = 0L, // downloaded bytes
    val retryCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromDownloadState(downloadState: DownloadState): EnhancedDownloadState {
            return EnhancedDownloadState(
                songId = downloadState.songId,
                isDownloading = downloadState.isDownloading,
                progress = downloadState.progress,
                isComplete = downloadState.isComplete,
                error = downloadState.error
            )
        }
    }
    
    fun toDownloadState(): DownloadState {
        return DownloadState(
            songId = songId,
            isDownloading = isDownloading,
            progress = progress,
            isComplete = isComplete,
            error = error
        )
    }
}
