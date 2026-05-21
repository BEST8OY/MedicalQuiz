package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    val imageSizeModifier = remember(block.width, block.height) {
        val w = block.width?.takeIf { it > 0 }
        val h = block.height?.takeIf { it > 0 }
        when {
            // Both dimensions known: cap width and fix ratio so Coil gets a bounded box
            w != null && h != null -> Modifier
                .widthIn(max = w.dp)
                .aspectRatio(w.toFloat() / h)
            // No dimensions: use a default ratio so Coil never receives an infinite height
            else -> Modifier
                .fillMaxWidth()
                .aspectRatio(DefaultMediaAspectRatio)
        }
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
                .then(imageSizeModifier)
                .clickable { onMediaClick(clickTarget) },
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

private const val DefaultMediaAspectRatio = 4f / 3f

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