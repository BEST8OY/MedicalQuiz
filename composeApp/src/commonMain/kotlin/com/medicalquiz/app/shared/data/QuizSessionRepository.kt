package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Manages quiz session state persistence for process death recovery and session history.
 */
class QuizSessionRepository {
    private val sessionFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/quiz_session.json"

    private val historyFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/quiz_session_history.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun saveSession(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
    ) {
        try {
            if (databaseName.isEmpty() || currentQuestionIndex < 0) {
                clearSession()
                return
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val existingSessionId = restoreSession()
                ?.takeIf { it.databaseName == databaseName }
                ?.id
                ?.takeIf { it.isNotBlank() }
            val session = QuizSession(
                id = existingSessionId ?: buildSessionId(databaseName, now),
                databaseName = databaseName,
                selectedSubjectIds = selectedSubjectIds.toList(),
                selectedSystemIds = selectedSystemIds.toList(),
                performanceFilter = performanceFilter,
                currentQuestionIndex = currentQuestionIndex,
                updatedAtEpochMillis = now,
            )
            writeSession(session)
            appendHistoryEntry(session)
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error saving quiz session", e)
        }
    }

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

    fun listHistory(): List<QuizSession> {
        return try {
            val content = FileSystemHelper.readText(historyFile) ?: return emptyList()
            json.decodeFromString<List<QuizSession>>(content)
                .sortedByDescending { it.updatedAtEpochMillis }
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error reading quiz history", e)
            emptyList()
        }
    }

    fun restoreHistoryEntry(entryId: String): QuizSession? {
        val entry = listHistory().firstOrNull { it.id == entryId } ?: return null
        return try {
            writeSession(entry)
            entry
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error restoring history entry", e)
            null
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        try {
            val updated = listHistory().filterNot { it.id == entryId }
            saveHistoryList(updated)
            val active = restoreSession()
            if (active?.id == entryId) {
                clearSession()
            }
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error deleting history entry", e)
        }
    }

    fun clearSession() {
        try {
            FileSystemHelper.delete(sessionFile)
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error clearing quiz session", e)
        }
    }

    private fun appendHistoryEntry(session: QuizSession) {
        val history = listHistory()
            .filterNot { it.id == session.id }
            .toMutableList()
        history.add(session)
        saveHistoryList(history)
    }

    private fun saveHistoryList(history: List<QuizSession>) {
        val bounded = history
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(MAX_HISTORY_ENTRIES)
        FileSystemHelper.writeText(historyFile, json.encodeToString(bounded))
    }

    private fun writeSession(session: QuizSession) {
        val jsonString = json.encodeToString(session)
        FileSystemHelper.writeText(sessionFile, jsonString)
    }

    private fun buildSessionId(databaseName: String, now: Long): String = "$databaseName-$now"

    @Serializable
    data class QuizSession(
        val id: String = "",
        val databaseName: String,
        val selectedSubjectIds: List<Long>,
        val selectedSystemIds: List<Long>,
        val performanceFilter: PerformanceFilter,
        val currentQuestionIndex: Int,
        val updatedAtEpochMillis: Long = 0,
    )

    companion object {
        private const val MAX_HISTORY_ENTRIES = 20
    }
}
