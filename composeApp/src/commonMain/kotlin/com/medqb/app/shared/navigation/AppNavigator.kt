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

    val canNavigateBack: Boolean
        get() = backStack.size > 1

    fun navigateTo(route: MedQBRoutes) {
        if (currentRoute != route) {
            backStack.add(route)
        }
    }

    fun navigateBack(): Boolean {
        if (!canNavigateBack) return false
        backStack.removeLastOrNull()
        return true
    }

    fun popToDatabaseSelection() {
        if (backStack.size > 1) {
            backStack.subList(1, backStack.size).clear()
        }
    }

    fun returnQuizToFilter(targetPaneName: String? = null) {
        val filterIndex = backStack.indexOfLast { it is MedQBRoutes.Filter }
        if (filterIndex >= 0) {
            if (backStack.size > filterIndex + 1) {
                backStack.subList(filterIndex + 1, backStack.size).clear()
            }
        } else {
            popToDatabaseSelection()
            navigateTo(MedQBRoutes.Filter(initialPaneName = targetPaneName))
        }
    }
}
