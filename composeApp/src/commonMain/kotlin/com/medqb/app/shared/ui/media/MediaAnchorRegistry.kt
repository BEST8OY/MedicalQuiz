package com.medqb.app.shared.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect

/**
 * Registry that tracks the root-relative bounds of media thumbnails.
 *
 * Used by [MediaViewerOverlayHost] to animate the media viewer surface directly
 * to and from the origin thumbnail with zero flicker or duplicate images.
 */
class MediaAnchorRegistry {
    private val _anchors = mutableStateMapOf<String, Rect>()

    fun register(key: String, bounds: Rect) {
        if (bounds.width > 0 && bounds.height > 0) {
            _anchors[key] = bounds
        }
    }

    fun unregister(key: String) {
        _anchors.remove(key)
    }

    fun getBounds(key: String): Rect? = _anchors[key]
}

val LocalMediaAnchorRegistry = compositionLocalOf<MediaAnchorRegistry?> { null }

@Composable
fun rememberMediaAnchorRegistry(): MediaAnchorRegistry = remember { MediaAnchorRegistry() }
