package com.medicalquiz.app.shared.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAppSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
