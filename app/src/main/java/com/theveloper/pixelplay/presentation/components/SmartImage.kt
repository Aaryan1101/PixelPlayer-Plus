package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import com.theveloper.pixelplay.R

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.ic_music_placeholder,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = true,
    targetSize: Size = Size(300, 300),
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val clippedModifier = modifier.clip(shape)
    var resolvedModel by remember(model) { mutableStateOf(model) }

    val displayModel = when (val candidate = resolvedModel) {
        is ImageRequest -> handleDirectModel(
            data = candidate.data,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: candidate
        else -> handleDirectModel(
            data = candidate,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: candidate
    }

    if (displayModel is ImageVector || displayModel is Painter || displayModel is ImageBitmap || displayModel is Bitmap) {
        // Already rendered inside handleDirectModel.
        return
    }

    val request = when (displayModel) {
        is ImageRequest -> displayModel
        else -> ImageRequest.Builder(context)
            .data(displayModel)
            .crossfade(crossfadeDurationMillis)
            .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .allowHardware(allowHardware)
            .memoryCacheKey(displayModel?.toString()?.plus("_${targetSize.width}x${targetSize.height}"))
            .diskCacheKey(displayModel?.toString()?.plus("_${targetSize.width}x${targetSize.height}"))
            .apply {
                size(targetSize)
            }
            .build()
    }

    Box(
        modifier = clippedModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            placeholder = painterResource(placeholderResId),
            error = painterResource(errorResId),
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha,
            onLoading = { state -> onState?.invoke(state) },
            onSuccess = { state -> onState?.invoke(state) },
            onError = { state ->
                youtubeThumbnailFallback(resolvedModel)?.let { resolvedModel = it }
                onState?.invoke(state)
            }
        )
    }
}

@Composable
private fun handleDirectModel(
    data: Any?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    alpha: Float
): Any? {
    return when (data) {
        is ImageVector -> {
            Image(
                imageVector = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Painter -> {
            Image(
                painter = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is ImageBitmap -> {
            Image(
                bitmap = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Bitmap -> {
            Image(
                bitmap = data.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        else -> null
    }
}

private fun youtubeThumbnailFallback(model: Any?): String? {
    val url = model as? String ?: return null
    if ("i.ytimg.com/vi/" !in url || !url.endsWith("/maxresdefault.jpg")) return null
    return url.removeSuffix("/maxresdefault.jpg") + "/hqdefault.jpg"
}
