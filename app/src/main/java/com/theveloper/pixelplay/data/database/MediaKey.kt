package com.theveloper.pixelplay.data.database

internal fun String.toStableMediaKey(): String {
    return when {
        startsWith("youtube_") -> "youtube:${removePrefix("youtube_")}"
        ':' in this -> this
        toLongOrNull() != null -> "local:$this"
        else -> "remote:$this"
    }
}

internal fun String.toLegacySongId(): String {
    return when {
        startsWith("youtube:") -> "youtube_${removePrefix("youtube:")}"
        startsWith("local:") -> removePrefix("local:")
        startsWith("remote:") -> removePrefix("remote:")
        else -> this
    }
}
