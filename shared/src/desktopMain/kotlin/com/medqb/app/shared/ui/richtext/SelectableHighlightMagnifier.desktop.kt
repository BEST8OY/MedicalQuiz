package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density

internal actual fun Modifier.platformSelectionMagnifier(
    sourceCenter: Density.() -> Offset,
): Modifier = this
