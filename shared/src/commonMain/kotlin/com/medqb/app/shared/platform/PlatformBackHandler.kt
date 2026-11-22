package com.medqb.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Multiplatform back gesture handler for in-screen states (e.g., zoomed media, selection modes).
 *
 * Integrates directly with Navigation 3's [androidx.navigationevent.compose.NavigationBackHandler].
 * Gracefully no-ops in @Preview and isolated tests where no [LocalNavigationEventDispatcherOwner] is present.
 */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    val currentOnBack by rememberUpdatedState(onBack)
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navState,
        isBackEnabled = enabled,
        onBackCompleted = { currentOnBack() },
    )
}
