package com.medqb.app.shared.ui.media

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RectangleShape
import com.medqb.app.shared.utils.HtmlUtils

/**
 * Bounded LRU cache mapping resolved media paths/URLs to their decoded aspect ratios (`width / height`).
 *
 * Pre-seeding known aspect ratios lets both inline rich-text slots and the full-screen media viewer
 * reserve matching proportions synchronously on frame 0 without post-decode layout jumps or
 * cross-composable snapshot invalidation storms.
 */
internal object MediaAspectRatioCache {
    private const val MAX_ENTRIES = 256
    private val ratios = object : LinkedHashMap<String, Float>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(mediaKey: String): Float? {
        if (mediaKey.isBlank()) return null
        synchronized(ratios) {
            return ratios[mediaKey]
        }
    }

    fun record(mediaKey: String, intrinsicSize: Size) {
        if (mediaKey.isBlank()) return
        if (intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            val ratio = intrinsicSize.width / intrinsicSize.height
            synchronized(ratios) {
                if (ratios[mediaKey] != ratio) {
                    ratios[mediaKey] = ratio
                }
            }
        }
    }

    fun clear() {
        synchronized(ratios) {
            ratios.clear()
        }
    }
}

internal fun mediaSharedElementKey(mediaRef: String): String {
    val normalized = HtmlUtils.normalizeFileName(mediaRef).lowercase()
    return "media_${normalized.ifBlank { mediaRef.trim().lowercase() }}"
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
