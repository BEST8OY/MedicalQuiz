package com.medicalquiz.app.shared.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on Desktop - no back handler concept on desktop platforms
}
