package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.rememberAsyncImagePainter
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.media.MediaAspectRatioCache
import com.medqb.app.shared.ui.media.mediaSharedElement
import com.medqb.app.shared.ui.media.mediaSharedElementKey
import com.medqb.app.shared.utils.HtmlUtils

/**
 * Renders a media element (image) with optional description.
 *
 * Inline images are tap-to-view surfaces that participate in Navigation 3 hero transitions using a
 * layout-independent semantic owner key derived from the block's section, index, and normalized ref.
 *
 * @param block The media block containing source, description, and layout information
 * @param onMediaClick Callback invoked when the media is clicked with `(mediaRef, sharedElementOwnerKey)`
 * @param blockIndex Structural block index within the parent [RichText]
 * @param mediaOwnerPrefix Optional semantic prefix (e.g. `"stem"`, `"explanation"`) disambiguating sections
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RichMedia(
    block: RichTextBlock.Media,
    onMediaClick: (mediaRef: String, sharedElementOwnerKey: String) -> Unit,
    blockIndex: Int = 0,
    mediaOwnerPrefix: String = "",
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }

    val clickTarget = remember(block.source, block.mediaRef) {
        val raw = block.mediaRef ?: extractMediaRef(block.source) ?: block.source
        HtmlUtils.normalizeFileName(raw).ifBlank { raw }
    }
    val ownerToken = remember(mediaOwnerPrefix, blockIndex, clickTarget) {
        computeMediaOwnerKey(mediaOwnerPrefix, blockIndex, clickTarget)
    }
    val sharedElementKey = remember(clickTarget) {
        mediaSharedElementKey(clickTarget)
    }

    val hasExplicitWidth = block.width?.let { it > 0 } == true
    val imageSizeModifier = remember(block.width) {
        val w = block.width?.takeIf { it > 0 }
        when {
            w != null -> Modifier
                .widthIn(max = minOf(w, MaxEmbeddedImageWidth.value.toInt()).dp)
            else -> Modifier
                .widthIn(max = MaxEmbeddedImageWidth)
                .fillMaxWidth()
        }
    }

    var knownAspectRatio by remember(mediaModel) {
        mutableStateOf(MediaAspectRatioCache.get(mediaModel))
    }

    val painter = rememberAsyncImagePainter(
        model = mediaModel,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            MediaAspectRatioCache.record(mediaModel, size)
            if (size.isSpecified && size.width > 0f && size.height > 0f) {
                knownAspectRatio = size.width / size.height
            }
        },
    )

    val aspectModifier = if (!hasExplicitWidth && knownAspectRatio != null) {
        Modifier.aspectRatio(knownAspectRatio!!)
    } else {
        Modifier
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val navAnimatedScope = animatedVisibilityScope ?: if (sharedTransitionScope != null) {
        LocalNavAnimatedContentScope.current
    } else null
    val activeKey = LocalActiveSharedElementKey.current?.value
    val sharedElementModifier = if (
        sharedTransitionScope != null &&
        navAnimatedScope != null &&
        activeKey == ownerToken
    ) {
        Modifier.mediaSharedElement(
            sharedElementKey = sharedElementKey,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = navAnimatedScope,
        )
    } else {
        Modifier
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
        // Place imageSizeModifier/aspectModifier outside sharedElementModifier with
        // propagateMinConstraints = true so settled layout obeys HTML bounds while
        // SharedBoundsNode's fixed transition constraints reach Image unobstructed.
        Box(
            modifier = Modifier
                .then(imageSizeModifier)
                .then(aspectModifier)
                .then(sharedElementModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Image,
                    onClick = { onMediaClick(clickTarget, ownerToken) },
                ),
            propagateMinConstraints = true,
        ) {
            Image(
                painter = painter,
                contentDescription = block.description,
                contentScale = ContentScale.Fit,
                modifier = if (hasExplicitWidth) Modifier else Modifier.fillMaxWidth(),
            )
        }
        block.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = block.alignment
            )
        }
    }
}

internal fun computeMediaOwnerKey(
    ownerPrefix: String,
    blockIndex: Int,
    mediaRef: String,
): String {
    val normalized = HtmlUtils.normalizeFileName(mediaRef).lowercase().ifBlank { mediaRef.trim().lowercase() }
    return if (ownerPrefix.isNotBlank()) "$ownerPrefix:$blockIndex:$normalized" else "$blockIndex:$normalized"
}

private val MaxEmbeddedImageWidth = 512.dp

/**
 * Resolves the media source to a Coil-compatible model.
 * Tries to use mediaRef first, falls back to extracting from source path.
 *
 * @param source The source URL or path
 * @param mediaRef Optional explicit media reference/filename
 * @return Coil model (file path string or URL)
 */
internal fun mediaModelForSource(source: String, mediaRef: String?): String {
    val rawName = mediaRef ?: extractMediaRef(source)
    val filename = rawName?.let { HtmlUtils.normalizeFileName(it) }?.takeIf { it.isNotBlank() }
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
