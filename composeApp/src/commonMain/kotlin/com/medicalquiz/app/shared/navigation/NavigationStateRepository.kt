package com.medicalquiz.app.shared.navigation

import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages navigation state persistence for process death recovery.
 * 
 * This allows the app to restore the user's navigation position after
 * the app is killed by the system (e.g., due to memory pressure).
 */
class NavigationStateRepository {
    private val navigationStateFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/navigation_state.json"

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false // Save space
    }

    /**
     * Saves the current navigation back stack to persistent storage.
     *
     * @param backStack The current list of routes in the back stack
     * @param selectedDatabase The name of the currently selected database (if any)
     */
    fun saveNavigationState(backStack: List<MedicalQuizRoutes>, selectedDatabase: String? = null) {
        try {
            // Don't save transient screen states - start fresh on those
            val filteredStack = backStack.filter { route ->
                route !is MedicalQuizRoutes.MediaViewer &&
                route !is MedicalQuizRoutes.HtmlViewer &&
                route !is MedicalQuizRoutes.SubjectSelection &&
                route !is MedicalQuizRoutes.SystemSelection &&
                route !is MedicalQuizRoutes.PerformanceSelection
            }

            if (filteredStack.isNotEmpty()) {
                val state = NavigationState(
                    routes = filteredStack,
                    selectedDatabase = selectedDatabase
                )
                val jsonString = json.encodeToString(state)
                FileSystemHelper.writeText(navigationStateFile, jsonString)
            } else {
                // Clear saved state if back stack is empty
                FileSystemHelper.delete(navigationStateFile)
            }
        } catch (e: Exception) {
            Logger.e("NavigationState", "Error saving navigation state", e)
        }
    }

    /**
     * Restores the navigation back stack from persistent storage.
     *
     * @return Pair of (routes, selectedDatabase) or null if no state was saved
     */
    fun restoreNavigationState(): Pair<List<MedicalQuizRoutes>, String?>? {
        return try {
            val content = FileSystemHelper.readText(navigationStateFile)
            if (content != null) {
                val state = json.decodeFromString<NavigationState>(content)
                // Validate the saved state - ensure it starts with DatabaseSelection
                if (state.routes.isNotEmpty() && state.routes.first() is MedicalQuizRoutes.DatabaseSelection) {
                    state.routes to state.selectedDatabase
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("NavigationState", "Error restoring navigation state", e)
            null
        }
    }

    /**
     * Clears the saved navigation state.
     */
    /**
     * Clears the saved navigation state.
     * 
     * @return true if the file was deleted successfully, false otherwise
     */
    fun clearNavigationState(): Boolean {
        return try {
            FileSystemHelper.delete(navigationStateFile)
        } catch (e: Exception) {
            Logger.e("NavigationState", "Error clearing navigation state", e)
            false
        }
    }

    @Serializable
    private data class NavigationState(
        val routes: List<MedicalQuizRoutes>,
        val selectedDatabase: String? = null
    )
}
