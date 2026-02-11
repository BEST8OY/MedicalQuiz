package com.medicalquiz.app.shared.ui.screens.media

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (!enabled) return

    PredictiveBackHandler { progress ->
        progress.collect { _ ->
            // Progress events can be used for custom animations if needed
            // For now, we just collect them to enable the predictive gesture
        }
        // Execute back action when gesture completes
        onBack()
    }
}
