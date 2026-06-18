package com.medicalquiz.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

@Composable
actual fun rememberBackStack(startRoute: NavKey): NavBackStack<NavKey> {
    @Suppress("UNCHECKED_CAST")
    return rememberNavBackStack(startRoute) as NavBackStack<NavKey>
}
