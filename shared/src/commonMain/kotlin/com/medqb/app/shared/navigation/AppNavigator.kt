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
        backStack.add(route)
    }

    /**
     * Navigates to [route], or pops back to the existing instance if [route] is already on the
     * back stack (e.g., unwinding `HtmlViewer` <-> `MediaViewer` link loops).
     */
    fun navigateOrPopTo(route: MedQBRoutes) {
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

    fun navigateBack(): Boolean {
        if (!canNavigateBack) return false
        backStack.removeLastOrNull()
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
        } else if (backStack.isEmpty()) {
            backStack.add(MedQBRoutes.DatabaseSelection)
        } else {
            // Replace in place so NavDisplay never observes a transiently empty back stack.
            backStack[0] = MedQBRoutes.DatabaseSelection
            if (backStack.size > 1) {
                backStack.subList(1, backStack.size).clear()
            }
        }
    }
}
