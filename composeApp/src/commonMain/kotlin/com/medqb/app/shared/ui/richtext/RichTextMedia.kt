package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImage
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.utils.HtmlUtils

import androidx.compose.ui.zIndex
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.snapBackZoomable

/**
 * Renders a media element (image) with optional description.
 *
 * @param block The media block containing source, description, and layout information
 * @param onMediaClick Callback invoked when the media is clicked
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RichMedia(block: RichTextBlock.Media, onMediaClick: (String) -> Unit) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }
    if (mediaModel == null) return

    val clickTarget = block.mediaRef ?: extractMediaRef(block.source) ?: block.source
    val imageSizeModifier = remember(block.width, block.height) {
        val w = block.width?.takeIf { it > 0 }
        when {
            w != null -> Modifier
                .widthIn(max = minOf(w, MaxEmbeddedImageWidth.value.toInt()).dp)
            else -> Modifier
                .fillMaxWidth()
                .widthIn(max = MaxEmbeddedImageWidth)
        }
    }

    val zoomState = rememberZoomState()
    val isZoomed = zoomState.scale > 1.001f

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    val activeKey = LocalActiveSharedElementKey.current?.value
    val sharedElementModifier = if (
        sharedTransitionScope != null &&
        activeKey == clickTarget
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "media_$clickTarget"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isZoomed) 10f else 0f)
            .padding(vertical = Spacing.Xs),
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
                .zIndex(if (isZoomed) 1f else 0f)
                .then(imageSizeModifier)
                .snapBackZoomable(
                    zoomState = zoomState,
                    onTap = { onMediaClick(clickTarget) },
                )
                .then(sharedElementModifier),
        )
        block.description?.let {
            androidx.compose.material3.Text(
                text = it,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.Xxs),
                textAlign = block.alignment
            )
        }
    }
}

private val MaxEmbeddedImageWidth = Layout.MediaMaxWidth

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
    return source.substringAfterLast('/', "")
        .substringBefore('?')
        .substringBefore('#')
        .trim()
        .takeIf { it.isNotBlank() }
}