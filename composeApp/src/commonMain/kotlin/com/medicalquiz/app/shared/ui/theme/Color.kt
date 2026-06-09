package com.medicalquiz.app.shared.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Seed color used as fallback for tonal palette generation
// on platforms that do not support dynamic color (pre-API 31, Desktop)
val PurpleSeed = Color(0xFF6750A4)

// Extended color tokens for custom semantic states
data class ExtendedColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightSuccessContainer = Color(0xFFCEEAD6)
private val LightOnSuccessContainer = Color(0xFF1B3A23)

private val DarkSuccessContainer = Color(0xFF4A6B52)
private val DarkOnSuccessContainer = Color(0xFFCEEAD6)

internal fun extendedColorsFor(isDark: Boolean) = if (isDark) {
    ExtendedColors(
        successContainer = DarkSuccessContainer,
        onSuccessContainer = DarkOnSuccessContainer,
    )
} else {
    ExtendedColors(
        successContainer = LightSuccessContainer,
        onSuccessContainer = LightOnSuccessContainer,
    )
}

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        successContainer = LightSuccessContainer,
        onSuccessContainer = LightOnSuccessContainer,
    )
}
