package com.medqb.app.shared.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val BaseTypography = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppTypography = Typography(
    displayLarge = BaseTypography.displayLarge,
    displayMedium = BaseTypography.displayMedium,
    displaySmall = BaseTypography.displaySmall,
    headlineLarge = BaseTypography.headlineLarge,
    headlineMedium = BaseTypography.headlineMedium,
    headlineSmall = BaseTypography.headlineSmall,
    titleLarge = BaseTypography.titleLarge,
    titleMedium = BaseTypography.titleMedium,
    titleSmall = BaseTypography.titleSmall,
    bodyLarge = BaseTypography.bodyLarge,
    bodyMedium = BaseTypography.bodyMedium,
    bodySmall = BaseTypography.bodySmall,
    labelLarge = BaseTypography.labelLarge,
    labelMedium = BaseTypography.labelMedium,
    labelSmall = BaseTypography.labelSmall,
    displayLargeEmphasized = BaseTypography.displayLarge.copy(fontWeight = FontWeight.Medium),
    displayMediumEmphasized = BaseTypography.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmallEmphasized = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLargeEmphasized = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMediumEmphasized = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmallEmphasized = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLargeEmphasized = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMediumEmphasized = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmallEmphasized = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLargeEmphasized = BaseTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMediumEmphasized = BaseTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmallEmphasized = BaseTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
    labelLargeEmphasized = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMediumEmphasized = BaseTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmallEmphasized = BaseTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
)
