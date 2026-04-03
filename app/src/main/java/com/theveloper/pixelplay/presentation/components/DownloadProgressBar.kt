package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadProgressBar(
    isDownloading: Boolean,
    progress: Float,
    isComplete: Boolean,
    hasError: Boolean,
    downloadSpeed: Long = 0L,
    timeRemaining: Long = 0L,
    retryCount: Int = 0,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    // Dynamic colors to match player theme
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onPrimaryColor: Color = MaterialTheme.colorScheme.onPrimary,
    primaryContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onPrimaryContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    onSurfaceColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "download_progress"
    )

    val backgroundColor = when {
        hasError -> MaterialTheme.colorScheme.errorContainer
        isComplete -> primaryContainerColor
        isDownloading -> surfaceColor
        else -> surfaceColor
    }

    val progressColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isComplete -> primaryColor
        else -> primaryColor
    }

    val contentColor = when {
        hasError -> MaterialTheme.colorScheme.onErrorContainer
        isComplete -> onPrimaryContainerColor
        isDownloading -> onSurfaceColor
        else -> onSurfaceColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(enabled = !isDownloading, onClick = onDownloadClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDownloading) {
            // Downloading state with progress bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(progressColor)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Progress text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (showDetails && downloadSpeed > 0) {
                        Text(
                            text = formatDownloadSpeed(downloadSpeed),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            // Idle, complete, or error state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon and text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when {
                        hasError -> if (retryCount > 0) Icons.Default.Refresh else Icons.Default.Close
                        isComplete -> Icons.Default.Check
                        else -> Icons.Default.Download
                    }
                    
                    val iconTint = when {
                        hasError -> MaterialTheme.colorScheme.error
                        isComplete -> primaryColor
                        else -> contentColor
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = when {
                            hasError -> "Download Error"
                            isComplete -> "Download Complete"
                            else -> "Download Song"
                        },
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )

                    val statusText = when {
                        hasError -> if (retryCount > 0) "Retry" else "Failed"
                        isComplete -> "Downloaded"
                        else -> "Download"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Additional info
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompactDownloadProgressBar(
    isDownloading: Boolean,
    progress: Float,
    isComplete: Boolean,
    hasError: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Dynamic colors to match player theme
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onPrimaryColor: Color = MaterialTheme.colorScheme.onPrimary,
    primaryContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onPrimaryContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    onSurfaceColor: Color = MaterialTheme.colorScheme.onSurface
) {
    DownloadProgressBar(
        isDownloading = isDownloading,
        progress = progress,
        isComplete = isComplete,
        hasError = hasError,
        onDownloadClick = onDownloadClick,
        modifier = modifier,
        showDetails = false,
        primaryColor = primaryColor,
        onPrimaryColor = onPrimaryColor,
        primaryContainerColor = primaryContainerColor,
        onPrimaryContainerColor = onPrimaryContainerColor,
        surfaceColor = surfaceColor,
        onSurfaceColor = onSurfaceColor
    )
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "${bytesPerSecond}B/s"
        bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024}KB/s"
        else -> "${bytesPerSecond / (1024 * 1024)}MB/s"
    }
}

private fun formatTimeRemaining(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}
