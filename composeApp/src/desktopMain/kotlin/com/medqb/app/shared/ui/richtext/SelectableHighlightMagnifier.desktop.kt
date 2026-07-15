package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

actual fun Modifier.platformSelectionMagnifier(
    sourceCenter: () -> Offset,
    magnifierCenter: () -> Offset,
): Modifier = this // No-op on Desktop
