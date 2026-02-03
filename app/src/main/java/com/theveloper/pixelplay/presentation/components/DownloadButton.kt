package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DownloadButton(
    isDownloading: Boolean,
    progress: Float,
    isComplete: Boolean,
    hasError: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "download_progress"
    )

    when {
        hasError -> {
            // Show error state (could be a retry icon)
            IconButton(
                onClick = onDownloadClick,
                modifier = modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Retry Download",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        isComplete -> {
            // Show completed state
            IconButton(
                onClick = { /* Already downloaded */ },
                modifier = modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Download Complete",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        isDownloading -> {
            // Show downloading state with circular progress
            IconButton(
                onClick = { /* Downloading */ },
                modifier = modifier
            ) {
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        else -> {
            // Show download button
            IconButton(
                onClick = onDownloadClick,
                modifier = modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Song",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SmallDownloadButton(
    isDownloading: Boolean,
    progress: Float,
    isComplete: Boolean,
    hasError: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "download_progress"
    )

    when {
        hasError -> {
            IconButton(
                onClick = onDownloadClick,
                modifier = modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Retry Download",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        isComplete -> {
            IconButton(
                onClick = { /* Already downloaded */ },
                modifier = modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Download Complete",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        isDownloading -> {
            IconButton(
                onClick = { /* Downloading */ },
                modifier = modifier.size(32.dp)
            ) {
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        else -> {
            IconButton(
                onClick = onDownloadClick,
                modifier = modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Song",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
