package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density

/**
 * Platform-specific magnifier modifier.
 * Android: real magnifier via [android.widget.Magnifier] (API 28+).
 * Other targets: no-op for now (hand-rolled magnifier can be added later).
 */
expect fun Modifier.platformSelectionMagnifier(
    sourceCenter: Density.() -> Offset,
    magnifierCenter: (Density.() -> Offset)? = null,
): Modifier
