package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Small imperative wrapper around the Navigation 3 back stack.
 *
 * Keeping stack mutation in one place makes app-level navigation decisions easier
 * to test and keeps the root App composable focused on rendering entries.
 */
class AppNavigator(
    private val backStack: MutableList<NavKey>,
) {
    val currentRoute: MedQBRoutes?
        get() = backStack.lastOrNull() as? MedQBRoutes

    fun navigateTo(route: MedQBRoutes) {
        if (currentRoute != route) {
            backStack.add(route)
        }
    }

    fun switchTo(route: MedQBRoutes) {
        if (currentRoute != route) {
            backStack.removeLastOrNull()
            backStack.add(route)
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    fun popToDatabaseSelection() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun returnQuizToFilter(targetPaneName: String? = null) {
        val filterIndex = backStack.indexOfLast { it is MedQBRoutes.Filter }
        if (filterIndex >= 0) {
            while (backStack.size > filterIndex + 1) {
                backStack.removeLastOrNull()
            }
        } else {
            popToDatabaseSelection()
            navigateTo(MedQBRoutes.Filter(initialPaneName = targetPaneName))
        }
    }
}
