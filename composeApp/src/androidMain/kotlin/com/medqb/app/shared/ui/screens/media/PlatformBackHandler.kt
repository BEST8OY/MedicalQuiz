package com.medqb.app.shared.ui.screens.media

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (!enabled) return

    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navState,
        isBackEnabled = enabled,
        onBackCancelled = { },
        onBackCompleted = onBack,
    )
}
