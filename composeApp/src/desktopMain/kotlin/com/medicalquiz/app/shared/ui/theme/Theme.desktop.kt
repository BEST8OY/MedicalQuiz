package com.medicalquiz.app.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun getPlatformColorScheme(darkTheme: Boolean): ColorScheme? {
    return if (darkTheme) darkColorScheme() else expressiveLightColorScheme()
}
