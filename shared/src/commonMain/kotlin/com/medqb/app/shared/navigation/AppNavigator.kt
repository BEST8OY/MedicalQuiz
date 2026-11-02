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
        if (currentRoute is MedQBRoutes.Quiz) {
            returnQuizToFilter()
        } else {
            backStack.removeLastOrNull()
        }
        return true
    }

    /**
     * Pops all entries back to [MedQBRoutes.DatabaseSelection].
     *
     * Finds the first [MedQBRoutes.DatabaseSelection] in the stack and clears any routes above it.
     * If no [MedQBRoutes.DatabaseSelection] is present, clears the entire stack and pushes
     * a new root [MedQBRoutes.DatabaseSelection].
     */
    fun popToDatabaseSelection() {
        val targetIndex = backStack.indexOfFirst { it is MedQBRoutes.DatabaseSelection }
        if (targetIndex >= 0) {
            if (backStack.size > targetIndex + 1) {
                backStack.subList(targetIndex + 1, backStack.size).clear()
            }
        } else {
            backStack.clear()
            backStack.add(MedQBRoutes.DatabaseSelection)
        }
    }

    /**
     * Returns from the quiz session to the filter screen.
     *
     * If a [MedQBRoutes.Filter] route is already in the back stack, pops all routes above it
     * while preserving the original [MedQBRoutes.Filter] instance identity. This maintains
     * its scoped ViewModel and saved state in the Navigation 3 entry decorators.
     *
     * If no [MedQBRoutes.Filter] exists in the back stack, resets to [MedQBRoutes.DatabaseSelection]
     * and navigates forward to a new [MedQBRoutes.Filter] initialized with [targetPaneName].
     */
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
