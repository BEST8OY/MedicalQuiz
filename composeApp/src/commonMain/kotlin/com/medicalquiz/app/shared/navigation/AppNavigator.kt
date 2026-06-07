package com.medicalquiz.app.shared.navigation

/**
 * Small imperative wrapper around the Navigation 3 back stack.
 *
 * Keeping stack mutation in one place makes app-level navigation decisions easier
 * to test and keeps the root App composable focused on rendering entries.
 */
class AppNavigator(
    private val backStack: MutableList<MedicalQuizRoutes>,
) {
    val currentRoute: MedicalQuizRoutes?
        get() = backStack.lastOrNull()

    fun navigateTo(route: MedicalQuizRoutes) {
        if (currentRoute != route) {
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

    fun returnQuizToFilter() {
        if (currentRoute is MedicalQuizRoutes.Quiz) {
            navigateBack()
        }
        if (currentRoute !is MedicalQuizRoutes.Filter) {
            popToDatabaseSelection()
            navigateTo(MedicalQuizRoutes.Filter)
        }
    }
}
