package com.medicalquiz.app.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun getPlatformColorScheme(darkTheme: Boolean): ColorScheme? {
    // Desktop uses the fallback expressive color scheme from Theme.kt
    // We return null to let the common code use LightColorScheme or DarkColorScheme
    return null
}
