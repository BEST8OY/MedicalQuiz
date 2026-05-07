package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.medicalquiz.app.shared.utils.HtmlUtils

/**
 * Renders a media element (image) with optional description.
 * 
 * @param block The media block containing source, description, and layout information
 * @param onMediaClick Callback invoked when the media is clicked
 */
@Composable
internal fun RichMedia(block: RichTextBlock.Media, onMediaClick: (String) -> Unit) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }
    if (mediaModel == null) return
    val clickTarget = block.mediaRef ?: block.source
    var aspectRatio by remember(block.width, block.height) {
        mutableStateOf(mediaAspectRatioFor(block.width, block.height, null, null))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = when (block.alignment) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        AsyncImage(
            model = mediaModel,
            contentDescription = block.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    aspectRatio?.let { Modifier.aspectRatio(it) }
                        ?: Modifier.heightIn(min = InlineMediaFallbackHeight)
                )
                .clickable { onMediaClick(clickTarget) },
            onState = { state ->
                if (aspectRatio == null && state is AsyncImagePainter.State.Success) {
                    val intrinsicSize = state.painter.intrinsicSize
                    aspectRatio = mediaAspectRatioFor(
                        width = block.width,
                        height = block.height,
                        intrinsicWidth = intrinsicSize.width,
                        intrinsicHeight = intrinsicSize.height,
                    )
                }
            },
        )
        block.description?.let {
            androidx.compose.material3.Text(
                text = it,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = block.alignment
            )
        }
    }
}

private val InlineMediaFallbackHeight = 180.dp

/**
 * Returns a positive width/height ratio from HTML dimensions or a decoded painter size.
 *
 * Some image decoders do not expose intrinsic size early enough for Compose to infer a
 * height from fillMaxWidth() alone. Supplying a ratio when one is available, and a
 * fallback height otherwise, prevents inline media from measuring to a nearly empty
 * clickable strip.
 */
internal fun mediaAspectRatioFor(
    width: Int?,
    height: Int?,
    intrinsicWidth: Float?,
    intrinsicHeight: Float?,
): Float? {
    if (width != null && height != null && width > 0 && height > 0) {
        return width.toFloat() / height.toFloat()
    }

    val decodedWidth = intrinsicWidth
    val decodedHeight = intrinsicHeight
    if (decodedWidth != null &&
        decodedHeight != null &&
        decodedWidth.isPositiveFinite() &&
        decodedHeight.isPositiveFinite()
    ) {
        return decodedWidth / decodedHeight
    }

    return null
}

private fun Float.isPositiveFinite(): Boolean = this > 0f && !isNaN() && !isInfinite()

/**
 * Resolves the media source to a Coil-compatible model.
 * Tries to use mediaRef first, falls back to extracting from source path.
 * 
 * @param source The source URL or path
 * @param mediaRef Optional explicit media reference/filename
 * @return Coil model (file path string or URL), or null if resolution fails
 */
internal fun mediaModelForSource(source: String, mediaRef: String?): Any? {
    val filename = mediaRef ?: extractMediaRef(source)
    if (filename != null) {
        HtmlUtils.getMediaPath(filename)?.let { path ->
            // Coil 3 supports file paths as strings
            return path
        }
    }
    if (source.startsWith("file://")) {
        return source.removePrefix("file://")
    }
    return source
}

/**
 * Extracts the filename from a path or URL.
 * 
 * @param source The source path or URL
 * @return The filename portion after the last '/', or null if blank
 */
internal fun extractMediaRef(source: String): String? {
    return source.substringAfterLast('/', "").takeIf { it.isNotBlank() }
}
