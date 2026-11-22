package com.medqb.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Creates a parameterized callback that drops invocations unless the current lifecycle
 * is at least [Lifecycle.State.RESUMED].
 *
 * Multiplatform counterpart to [androidx.lifecycle.compose.dropUnlessResumed] supporting a single parameter.
 */
@Composable
fun <T> rememberDropUnlessResumed(
    block: (T) -> Unit,
): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBlock by rememberUpdatedState(block)
    return remember(lifecycleOwner) {
        { param: T ->
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                currentBlock(param)
            }
        }
    }
}
