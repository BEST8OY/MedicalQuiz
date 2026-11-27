package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.rememberAsyncImagePainter
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.utils.HtmlUtils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.SnapBackZoomableBox
import net.engawapg.lib.zoomable.rememberZoomState

/**
 * Renders a media element (image) with optional description.
 *
 * @param block The media block containing source, description, and layout information
 * @param onMediaClick Callback invoked when the media is clicked
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalZoomableApi::class)
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
    val instanceToken = remember { Any() }
    val sharedMediaCoordinator = LocalSharedMediaCoordinator.current
    DisposableEffect(sharedMediaCoordinator, clickTarget, instanceToken) {
        sharedMediaCoordinator.register(clickTarget, instanceToken)
        onDispose {
            sharedMediaCoordinator.unregister(clickTarget, instanceToken)
        }
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

    val zoomState = rememberZoomState()
    // Hoist the painter above SnapBackZoomableBox so toggling the zoom overlay never disposes
    // and reloads AsyncImagePainter (which would collapse height to 0 for a frame on release and
    // clamp the parent ScrollState).
    val painter = rememberAsyncImagePainter(
        model = mediaModel,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            zoomState.setContentSize(state.painter.intrinsicSize)
        },
    )

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val navAnimatedScope = animatedVisibilityScope ?: if (sharedTransitionScope != null) {
        LocalNavAnimatedContentScope.current
    } else null
    val activeKey = LocalActiveSharedElementKey.current?.value
    val motionScheme = MaterialTheme.motionScheme
    val defaultSpatialSpec = motionScheme.defaultSpatialSpec<Rect>()

    // Uses sharedElement with Material 3 Expressive motionScheme for pure single-element
    // hero transition without duplicate image crossfade artifacts.
    val sharedElementModifier = if (
        sharedTransitionScope != null &&
        navAnimatedScope != null &&
        activeKey == clickTarget &&
        sharedMediaCoordinator.isOwner(clickTarget, instanceToken)
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "media_$clickTarget"),
                animatedVisibilityScope = navAnimatedScope,
                boundsTransform = { _, _ -> defaultSpatialSpec },
                clipInOverlayDuringTransition = OverlayClip(RectangleShape),
            )
        }
    } else Modifier

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
        SnapBackZoomableBox(
            modifier = Modifier
                .then(imageSizeModifier)
                .then(sharedElementModifier),
            zoomState = zoomState,
            scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
            onTap = {
                sharedMediaCoordinator.prefer(clickTarget, instanceToken)
                onMediaClick(clickTarget)
            },
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

@Stable
internal class SharedMediaCoordinator {
    private val registrations = mutableStateMapOf<String, List<Any>>()
    private val preferred = mutableStateMapOf<String, Any>()

    fun register(key: String, token: Any) {
        val current = registrations[key].orEmpty()
        if (current.none { it === token }) {
            registrations[key] = current + token
        }
    }

    fun unregister(key: String, token: Any) {
        val current = registrations[key] ?: return
        val updated = current.filterNot { it === token }
        if (updated.isEmpty()) {
            registrations.remove(key)
        } else {
            registrations[key] = updated
        }
        if (preferred[key] === token) {
            preferred.remove(key)
        }
    }

    fun prefer(key: String, token: Any) {
        preferred[key] = token
    }

    fun isOwner(key: String, token: Any): Boolean {
        val list = registrations[key].orEmpty()
        val pref = preferred[key]
        if (pref != null && list.any { it === pref }) {
            return pref === token
        }
        return list.firstOrNull() === token
    }
}

internal val LocalSharedMediaCoordinator = compositionLocalOf { SharedMediaCoordinator() }

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