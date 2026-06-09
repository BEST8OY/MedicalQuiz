package com.medicalquiz.app.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable

// Light color scheme — official M3 Expressive defaults
private val LightColorScheme = expressiveLightColorScheme()

// Dark color scheme — official M3 default dark scheme
private val DarkColorScheme = darkColorScheme()

@Composable
expect fun getPlatformColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = getPlatformColorScheme(useDarkTheme)
        ?: if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = Shapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
