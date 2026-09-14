package com.medqb.app.shared.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * State holder for the in-place media viewer overlay.
 *
 * Replaces route-based navigation into [MedQBRoutes.MediaViewer], allowing a single
 * persistent image surface to animate seamlessly between thumbnail bounds and fullscreen.
 */
class MediaOverlayState {
    var isVisible by mutableStateOf(false)
        private set

    var mediaFiles by mutableStateOf<List<String>>(emptyList())
        private set

    var initialIndex by mutableIntStateOf(0)
        private set

    fun open(files: List<String>, index: Int = 0) {
        if (files.isEmpty()) return
        mediaFiles = files
        initialIndex = index.coerceIn(0, (files.size - 1).coerceAtLeast(0))
        isVisible = true
    }

    fun close() {
        isVisible = false
        mediaFiles = emptyList()
        initialIndex = 0
    }
}

@Composable
fun rememberMediaOverlayState(): MediaOverlayState = remember { MediaOverlayState() }
