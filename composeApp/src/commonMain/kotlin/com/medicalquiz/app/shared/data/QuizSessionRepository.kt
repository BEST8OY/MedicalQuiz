package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages quiz session state persistence for process death recovery.
 *
 * This allows the app to restore the user's quiz session (filters, current question position)
 * after the app is killed by the system (e.g., due to memory pressure).
 */
class QuizSessionRepository {
    private val sessionFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/quiz_session.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Saves the current quiz session state to persistent storage.
     *
     * @param databaseName The name of the currently selected database
     * @param selectedSubjectIds The set of selected subject IDs
     * @param selectedSystemIds The set of selected system IDs
     * @param performanceFilter The current performance filter setting
     * @param currentQuestionIndex The index of the current question in the filtered list
     */
    fun saveSession(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int
    ) {
        try {
            // Only save if we have a valid database and question state
            if (databaseName.isEmpty() || currentQuestionIndex < 0) {
                clearSession()
                return
            }

            val session = QuizSession(
                databaseName = databaseName,
                selectedSubjectIds = selectedSubjectIds.toList(),
                selectedSystemIds = selectedSystemIds.toList(),
                performanceFilter = performanceFilter,
                currentQuestionIndex = currentQuestionIndex
            )
            val jsonString = json.encodeToString(session)
            FileSystemHelper.writeText(sessionFile, jsonString)
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error saving quiz session", e)
        }
    }

    /**
     * Restores the quiz session state from persistent storage.
     *
     * @return The saved session or null if no session was saved or restoration failed
     */
    fun restoreSession(): QuizSession? {
        return try {
            val content = FileSystemHelper.readText(sessionFile)
            if (content != null) {
                json.decodeFromString<QuizSession>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error restoring quiz session", e)
            null
        }
    }

    /**
     * Clears the saved quiz session.
     */
    fun clearSession() {
        try {
            FileSystemHelper.delete(sessionFile)
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error clearing quiz session", e)
        }
    }

    /**
     * Data class representing a saved quiz session.
     */
    @Serializable
    data class QuizSession(
        val databaseName: String,
        val selectedSubjectIds: List<Long>,
        val selectedSystemIds: List<Long>,
        val performanceFilter: PerformanceFilter,
        val currentQuestionIndex: Int
    )
}
