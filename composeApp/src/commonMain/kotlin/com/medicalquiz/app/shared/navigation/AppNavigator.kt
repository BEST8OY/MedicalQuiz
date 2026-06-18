package com.medicalquiz.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import com.medicalquiz.app.shared.platform.Logger

/**
 * Small imperative wrapper around the Navigation 3 back stack.
 *
 * Keeping stack mutation in one place makes app-level navigation decisions easier
 * to test and keeps the root App composable focused on rendering entries.
 */
class AppNavigator(
    private val backStack: MutableList<NavKey>,
) {
    val currentRoute: MedicalQuizRoutes?
        get() = backStack.lastOrNull() as? MedicalQuizRoutes

    fun navigateTo(route: MedicalQuizRoutes) {
        Logger.d("AppNavigator", "navigateTo: $route (current=${currentRoute}, stackSize=${backStack.size})")
        if (currentRoute != route) {
            backStack.add(route)
        } else {
            Logger.d("AppNavigator", "navigateTo: skipped — already on $route")
        }
    }

    fun navigateBack(): Boolean {
        Logger.d("AppNavigator", "navigateBack (current=${currentRoute}, stackSize=${backStack.size})")
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    fun popToDatabaseSelection() {
        Logger.d("AppNavigator", "popToDatabaseSelection (stackSize=${backStack.size})")
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun returnQuizToFilter() {
        Logger.d("AppNavigator", "returnQuizToFilter (current=${currentRoute})")
        if (currentRoute is MedicalQuizRoutes.Quiz) {
            navigateBack()
        }
        if (currentRoute !is MedicalQuizRoutes.Filter) {
            popToDatabaseSelection()
            navigateTo(MedicalQuizRoutes.Filter)
        }
    }
}
