package com.medicalquiz.app.shared.navigation

import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
        prettyPrint = false
    }

    /**
     * Saves the current navigation back stack to persistent storage.
     *
     * @param backStack The current list of routes in the back stack
     * @param selectedDatabase The name of the currently selected database (if any)
     * @param quizLaunchSource The quiz launch source to persist
     */
    fun saveNavigationState(
        backStack: List<MedicalQuizRoutes>,
        selectedDatabase: String? = null,
        quizLaunchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    ) {
        runCatching {
            val persistentRoutes = backStack
                .filter { it.isPersistent }
                .takeIf { it.isNotEmpty() }
                ?: run {
                    FileSystemHelper.delete(navigationStateFile)
                    return
                }

            val state = NavigationState(
                routes = persistentRoutes,
                selectedDatabase = selectedDatabase,
                quizLaunchSource = quizLaunchSource,
            )
            FileSystemHelper.writeText(navigationStateFile, json.encodeToString(state))
        }.onFailure {
            Logger.e("NavigationState", "Error saving navigation state", it)
        }
    }

    suspend fun saveNavigationStateAsync(
        backStack: List<MedicalQuizRoutes>,
        selectedDatabase: String? = null,
        quizLaunchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    ) = withContext(Dispatchers.IO) {
        saveNavigationState(backStack, selectedDatabase, quizLaunchSource)
    }

    /**
     * Restores the navigation back stack from persistent storage.
     *
     * @return [NavigationSnapshot] or null if no valid state was saved
     */
    fun restoreNavigationState(): NavigationSnapshot? {
        return runCatching {
            val content = FileSystemHelper.readText(navigationStateFile) ?: return null
            val state = json.decodeFromString<NavigationState>(content)
            val sanitizedRoutes = MedicalQuizRoutes.sanitizeRestoredBackStack(state.routes)
            if (sanitizedRoutes == null) {
                clearNavigationState()
                return null
            }
            NavigationSnapshot(
                routes = sanitizedRoutes,
                selectedDatabase = state.selectedDatabase,
                quizLaunchSource = state.quizLaunchSource,
            )
        }.getOrElse {
            handleRestoreError(it)
            null
        }
    }

    suspend fun restoreNavigationStateAsync(): NavigationSnapshot? =
        withContext(Dispatchers.IO) {
            restoreNavigationState()
        }

    /**
     * Clears the saved navigation state.
     *
     * @return true if the file was deleted successfully, false otherwise
     */
    fun clearNavigationState(): Boolean {
        return runCatching { FileSystemHelper.delete(navigationStateFile) }
            .onFailure { Logger.e("NavigationState", "Error clearing navigation state", it) }
            .getOrDefault(false)
    }

    suspend fun clearNavigationStateAsync(): Boolean = withContext(Dispatchers.IO) {
        clearNavigationState()
    }

    private fun handleRestoreError(throwable: Throwable) {
        if (throwable is SerializationException) {
            Logger.e("NavigationState", "Invalid navigation payload, clearing corrupted file", throwable)
            runCatching { FileSystemHelper.delete(navigationStateFile) }
                .onFailure { Logger.e("NavigationState", "Failed to delete corrupted navigation file", it) }
            return
        }
        Logger.e("NavigationState", "Error restoring navigation state", throwable)
    }

    @Serializable
    private data class NavigationState(
        val routes: List<MedicalQuizRoutes>,
        val selectedDatabase: String? = null,
        val quizLaunchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    )
}
