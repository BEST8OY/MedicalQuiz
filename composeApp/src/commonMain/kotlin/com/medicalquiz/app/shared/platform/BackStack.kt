package com.medicalquiz.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Creates and remembers a navigation back stack that survives process death.
 *
 * On Android, uses the Android-only Navigation 3 library which properly supports
 * SavedStateHandle-based persistence. On Desktop, uses the KMP Navigation 3 library
 * with SavedStateConfiguration.
 */
@Composable
expect fun rememberBackStack(startRoute: NavKey): NavBackStack<NavKey>
