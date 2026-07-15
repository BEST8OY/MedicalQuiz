package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.platformSelectionMagnifier(
    sourceCenter: Density.() -> Offset,
    magnifierCenter: Density.() -> Offset,
): Modifier = this.magnifier(
    sourceCenter = sourceCenter,
    magnifierCenter = magnifierCenter,
    shape = CircleShape,
)
