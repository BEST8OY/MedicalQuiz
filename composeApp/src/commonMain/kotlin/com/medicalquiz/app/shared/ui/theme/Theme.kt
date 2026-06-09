package com.medicalquiz.app.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.medicalquiz.app.shared.data.models.HighlightColor

@Immutable
data class HighlightColorScheme(
    val yellowContainer: Color,
    val onYellowContainer: Color,
    val greenContainer: Color,
    val onGreenContainer: Color,
    val blueContainer: Color,
    val onBlueContainer: Color,
    val pinkContainer: Color,
    val onPinkContainer: Color,
    val orangeContainer: Color,
    val onOrangeContainer: Color
)

private val LightHighlightColors = HighlightColorScheme(
    yellowContainer = Color(0xFFFFF9C4), onYellowContainer = Color(0xFF251C00),
    greenContainer = Color(0xFFE8F5E9),  onGreenContainer = Color(0xFF002106),
    blueContainer = Color(0xFFE3F2FD),   onBlueContainer = Color(0xFF001E3C),
    pinkContainer = Color(0xFFFCE4EC),   onPinkContainer = Color(0xFF3B0018),
    orangeContainer = Color(0xFFFFE0B2), onOrangeContainer = Color(0xFF2D1300)
)

private val DarkHighlightColors = HighlightColorScheme(
    yellowContainer = Color(0xFF554600), onYellowContainer = Color(0xFFFFF176),
    greenContainer = Color(0xFF003912),  onGreenContainer = Color(0xFFB9F6CA),
    blueContainer = Color(0xFF00325A),   onBlueContainer = Color(0xFFE3F2FD),
    pinkContainer = Color(0xFF5C0028),   onPinkContainer = Color(0xFFFFB8D1),
    orangeContainer = Color(0xFF562300), onOrangeContainer = Color(0xFFFFCC80)
)

val LocalHighlightColorScheme = staticCompositionLocalOf { LightHighlightColors }

object HighlightTheme {
    val colors: HighlightColorScheme
        @Composable get() = LocalHighlightColorScheme.current
}

fun HighlightColor.toContainerColors(scheme: HighlightColorScheme): Pair<Color, Color> {
    return when (this) {
        HighlightColor.YELLOW -> scheme.yellowContainer to scheme.onYellowContainer
        HighlightColor.GREEN  -> scheme.greenContainer to scheme.onGreenContainer
        HighlightColor.BLUE   -> scheme.blueContainer to scheme.onBlueContainer
        HighlightColor.PINK   -> scheme.pinkContainer to scheme.onPinkContainer
        HighlightColor.ORANGE -> scheme.orangeContainer to scheme.onOrangeContainer
    }
}

// Branded light scheme using custom palette
private val BrandedLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = InversePrimaryLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surfaceDim = SurfaceDimLight,
    surface = SurfaceLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    scrim = ScrimLight,
)

// Branded dark scheme using custom palette
private val BrandedDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = InversePrimaryDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    surfaceDim = SurfaceDimDark,
    surface = SurfaceDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    scrim = ScrimDark,
)

@Composable
expect fun getPlatformColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    useExpressiveFallback: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = getPlatformColorScheme(useDarkTheme)
        ?: if (useExpressiveFallback) {
            // TODO: replace darkColorScheme() with expressiveDarkColorScheme()
            //       when available in Compose Multiplatform M3
            if (useDarkTheme) darkColorScheme() else expressiveLightColorScheme()
        } else {
            if (useDarkTheme) BrandedDarkColorScheme else BrandedLightColorScheme
        }

    val highlightColorScheme = if (useDarkTheme) DarkHighlightColors else LightHighlightColors

    CompositionLocalProvider(LocalHighlightColorScheme provides highlightColorScheme) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = Shapes,
            content = content
        )
    }
}
