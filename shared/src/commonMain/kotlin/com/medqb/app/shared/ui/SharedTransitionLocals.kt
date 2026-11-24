package com.medqb.app.shared.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalActiveSharedElementKey = compositionLocalOf<MutableState<String?>?> { null }

/**
 * Keyed registry of active snackbar bottom clearance requests so overlapping
 * navigation transitions or multi-pop routes cannot overwrite or restore stale padding.
 */
@Stable
class SnackbarClearanceRegistry(
    private val defaultPadding: Dp = 12.dp,
) {
    private val entries = mutableStateMapOf<Any, Dp>()

    val bottomPadding: Dp
        get() = entries.values.maxOrNull() ?: defaultPadding

    fun register(key: Any, clearance: Dp) {
        entries[key] = clearance
    }

    fun unregister(key: Any) {
        entries.remove(key)
    }
}

val LocalSnackbarBottomPadding = compositionLocalOf { SnackbarClearanceRegistry() }
