package com.medqb.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navState,
        isBackEnabled = enabled,
        onBackCancelled = { },
        onBackCompleted = { currentOnBack() },
    )
}
