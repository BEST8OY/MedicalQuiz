package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density

actual fun Modifier.platformSelectionMagnifier(
    sourceCenter: Density.() -> Offset,
    magnifierCenter: Density.() -> Offset,
): Modifier = this // No-op on Desktop
