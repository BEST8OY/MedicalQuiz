package com.medqb.app.shared.ui.media

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RectangleShape

/**
 * Snapshot-backed cache mapping media references to their decoded aspect ratios (`width / height`).
 *
 * Pre-seeding known aspect ratios lets both inline rich-text slots and the full-screen media viewer
 * reserve matching proportions synchronously on frame 0 without post-decode layout jumps.
 */
internal object MediaAspectRatioCache {
    private val ratios = mutableStateMapOf<String, Float>()

    fun get(mediaKey: String): Float? = ratios[mediaKey]

    fun record(mediaKey: String, intrinsicSize: Size) {
        if (intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            val ratio = intrinsicSize.width / intrinsicSize.height
            if (ratios[mediaKey] != ratio) {
                ratios[mediaKey] = ratio
            }
        }
    }
}

/**
 * Attaches the shared element modifier used by both inline media thumbnails and the full-screen
 * viewer so hero bounds transitions share one consistent Material 3 spatial curve and clip shape.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.mediaSharedElement(
    sharedElementKey: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(sharedTransitionScope) {
        this@mediaSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> defaultSpatialSpec },
            clipInOverlayDuringTransition = OverlayClip(RectangleShape),
        )
    }
}
