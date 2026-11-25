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
        if (currentRoute == route) return
        val existingIndex = backStack.indexOf(route)
        if (existingIndex >= 0) {
            if (backStack.size > existingIndex + 1) {
                backStack.subList(existingIndex + 1, backStack.size).clear()
            }
        } else {
            backStack.add(route)
        }
    }

    /**
     * Navigates to [destination] only if [expectedCurrent] is currently at the top of the stack.
     */
    fun navigateIfCurrent(expectedCurrent: MedQBRoutes, destination: MedQBRoutes): Boolean {
        if (currentRoute != expectedCurrent || currentRoute == destination) return false
        navigateTo(destination)
        return true
    }

    fun navigateBack(): Boolean {
        if (!canNavigateBack) return false
        backStack.removeLastOrNull()
        return true
    }

    /**
     * Pops the top entry only if [expectedCurrent] is currently at the top of the stack.
     */
    fun popIfCurrent(expectedCurrent: MedQBRoutes): Boolean {
        if (currentRoute != expectedCurrent) return false
        return navigateBack()
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
}
