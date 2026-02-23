package com.medicalquiz.app.shared.ui.screens.media

import androidx.compose.runtime.Composable
import androidx.navigationevent.compose.NavigationBackHandler

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (!enabled) return

    NavigationBackHandler(onBack = onBack)
}
