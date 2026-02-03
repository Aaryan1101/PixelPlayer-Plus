package com.theveloper.pixelplay.data.model

data class DownloadState(
    val songId: String,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null
)
