package com.medqb.app.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.LocalSnackbarBottomPadding

/**
 * Canonical content maximum width tokens for responsive centering across screens.
 */
object ContentMaxWidth {
    val Readable: Dp = 680.dp
    val Standard: Dp = 840.dp
    val TwoPane: Dp = 1280.dp
    val MediaControls: Dp = 560.dp
}

/**
 * Centers its [content] horizontally while constraining its maximum width to [maxWidth].
 */
@Composable
fun ResponsiveContentContainer(
    maxWidth: Dp = ContentMaxWidth.Standard,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxWidth),
            content = content,
        )
    }
}

/**
 * Coordinates [LocalSnackbarBottomPadding] with floating bottom toolbars so snackbars
 * render above active bottom controls without colliding.
 */
@Composable
fun SnackbarBottomClearanceEffect(
    clearance: Dp,
    active: Boolean = true,
) {
    val registry = LocalSnackbarBottomPadding.current
    val key = remember { Any() }
    DisposableEffect(registry, key, active, clearance) {
        if (active) {
            registry.register(key, clearance)
        } else {
            registry.unregister(key)
        }
        onDispose {
            registry.unregister(key)
        }
    }
}
