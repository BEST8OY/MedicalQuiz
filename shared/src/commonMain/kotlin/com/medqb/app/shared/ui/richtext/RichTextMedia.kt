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
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.medqb.app.shared.utils.HtmlUtils

/**
 * Renders a media element (image) with optional description.
 *
 * Inline images are tap-to-view surfaces that participate in Navigation 3 hero transitions using a
 * deterministic structural shared element key derived from the block's composition slot.
 *
 * @param block The media block containing source, description, and layout information
 * @param onMediaClick Callback invoked when the media is clicked with `(mediaRef, sharedElementKey)`
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RichMedia(
    block: RichTextBlock.Media,
    onMediaClick: (String) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }

    val clickTarget = block.mediaRef ?: extractMediaRef(block.source) ?: block.source
    val compositeKey = currentCompositeKeyHashCode
    val ownerToken = remember(clickTarget, compositeKey) {
        "$clickTarget#$compositeKey"
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

    val painter = rememberAsyncImagePainter(
        model = mediaModel,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            MediaAspectRatioCache.record(clickTarget, state.painter.intrinsicSize)
        },
    )

    val knownAspectRatio = MediaAspectRatioCache.get(clickTarget)
    val aspectModifier = if (!hasExplicitWidth && knownAspectRatio != null) {
        Modifier.aspectRatio(knownAspectRatio)
    } else {
        Modifier
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val navAnimatedScope = animatedVisibilityScope ?: if (sharedTransitionScope != null) {
        LocalNavAnimatedContentScope.current
    } else null
    val activeKeyState = LocalActiveSharedElementKey.current
    val activeKey = activeKeyState?.value
    val sharedElementModifier = if (
        sharedTransitionScope != null &&
        navAnimatedScope != null &&
        activeKey == ownerToken
    ) {
        Modifier.mediaSharedElement(
            sharedElementKey = "media_$clickTarget",
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
                    onClick = {
                        activeKeyState?.value = ownerToken
                        onMediaClick(clickTarget)
                    },
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