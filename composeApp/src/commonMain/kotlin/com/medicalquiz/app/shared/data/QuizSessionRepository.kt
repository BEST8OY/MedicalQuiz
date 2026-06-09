package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Manages quiz session persistence for process death recovery and session history.
 */
class QuizSessionRepository {
    private val _historyEntries = MutableStateFlow<List<QuizSession>>(emptyList())
    val historyEntries: StateFlow<List<QuizSession>> = _historyEntries.asStateFlow()

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
        appendToHistory: Boolean = true,
        isLoggingEnabled: Boolean = false,
    ): String {
        if (databaseName.isBlank() || currentQuestionIndex < 0) {
            clearSession()
            return ""
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = resolveSessionId(
            databaseName = databaseName,
            selectedSubjectIds = selectedSubjectIds,
            selectedSystemIds = selectedSystemIds,
            performanceFilter = performanceFilter,
            now = now,
        )
        val session = QuizSession(
            id = sessionId,
            databaseName = databaseName,
            selectedSubjectIds = selectedSubjectIds.toSortedSet().toList(),
            selectedSystemIds = selectedSystemIds.toSortedSet().toList(),
            performanceFilter = performanceFilter,
            currentQuestionIndex = currentQuestionIndex,
            updatedAtEpochMillis = now,
            isLoggingEnabled = isLoggingEnabled,
        )

        runCatching { writeSession(session) }
            .onFailure { Logger.e("QuizSession", "Error saving active session", it) }

        if (appendToHistory) {
            runCatching { appendHistoryEntry(session) }
                .onFailure { Logger.e("QuizSession", "Error appending session history", it) }
        }

        return sessionId
    }

    suspend fun saveSessionAsync(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        appendToHistory: Boolean = true,
        isLoggingEnabled: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val sessionId = saveSession(
            databaseName = databaseName,
            selectedSubjectIds = selectedSubjectIds,
            selectedSystemIds = selectedSystemIds,
            performanceFilter = performanceFilter,
            currentQuestionIndex = currentQuestionIndex,
            appendToHistory = appendToHistory,
            isLoggingEnabled = isLoggingEnabled,
        )
        if (appendToHistory) {
            _historyEntries.value = listHistory()
        }
        sessionId
    }

    fun restoreSession(): QuizSession? =
        readFromFile<QuizSession>(sessionFile, ReadContext.Session)?.normalized()

    suspend fun restoreSessionAsync(): QuizSession? = withContext(Dispatchers.IO) {
        restoreSession()
    }

    fun listHistory(): List<QuizSession> =
        readFromFile<List<QuizSession>>(historyFile, ReadContext.History)
            ?.map { it.normalized() }
            ?.sortedByDescending { it.updatedAtEpochMillis }
            ?: emptyList()

    suspend fun listHistoryAsync(): List<QuizSession> = withContext(Dispatchers.IO) {
        listHistory()
    }

    suspend fun refreshHistoryAsync(): List<QuizSession> = withContext(Dispatchers.IO) {
        val history = listHistory()
        _historyEntries.value = history
        history
    }

    fun restoreHistoryEntry(entryId: String): QuizSession? {
        val entry = listHistory().firstOrNull { it.id == entryId } ?: return null
        return runCatching {
            writeSession(entry)
            entry
        }.getOrElse {
            Logger.e("QuizSession", "Error restoring history entry", it)
            null
        }
    }

    suspend fun restoreHistoryEntryAsync(entryId: String): QuizSession? = withContext(Dispatchers.IO) {
        restoreHistoryEntry(entryId).also {
            _historyEntries.value = listHistory()
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        deleteHistoryEntries(setOf(entryId))
    }

    fun deleteHistoryEntries(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        runCatching {
            deleteHistoryEntriesStrict(entryIds)
        }.onFailure {
            Logger.e("QuizSession", "Error deleting history entries", it)
        }
    }

    suspend fun deleteHistoryEntriesAsync(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        deleteHistoryEntries(entryIds)
        _historyEntries.value = listHistory()
    }

    suspend fun deleteHistoryEntriesStrictAsync(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isEmpty()) return@withContext
        deleteHistoryEntriesStrict(entryIds)
        _historyEntries.value = listHistory()
    }

    fun renameHistoryEntry(entryId: String, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        runCatching {
            val history = listHistory().toMutableList()
            val index = history.indexOfFirst { it.id == entryId }
            if (index < 0) return@runCatching
            history[index] = history[index].copy(entryName = trimmedName)
            saveHistoryList(history)

            val activeSession = restoreSession()
            if (activeSession?.id == entryId) {
                writeSession(activeSession.copy(entryName = trimmedName))
            }
        }.onFailure {
            Logger.e("QuizSession", "Error renaming history entry", it)
        }
    }

    suspend fun renameHistoryEntryAsync(entryId: String, newName: String) = withContext(Dispatchers.IO) {
        renameHistoryEntry(entryId, newName)
        _historyEntries.value = listHistory()
    }

    fun clearSession() {
        runCatching { FileSystemHelper.delete(sessionFile) }
            .onFailure { Logger.e("QuizSession", "Error clearing quiz session", it) }
    }

    suspend fun clearSessionAsync() = withContext(Dispatchers.IO) {
        clearSession()
    }

    private inline fun <reified T> readFromFile(path: String, context: ReadContext): T? {
        return runCatching {
            val content = FileSystemHelper.readText(path) ?: return null
            json.decodeFromString<T>(content)
        }.getOrElse {
            handleReadError(path = path, context = context, throwable = it)
            null
        }
    }

    private fun resolveSessionId(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        now: Long,
    ): String {
        val existingSession = restoreSession() ?: return buildSessionId(databaseName, now)
        val matchesContext = existingSession.databaseName == databaseName &&
            existingSession.selectedSubjectIds.toSet() == selectedSubjectIds &&
            existingSession.selectedSystemIds.toSet() == selectedSystemIds &&
            existingSession.performanceFilter == performanceFilter
        return if (matchesContext && existingSession.id.isNotBlank()) {
            existingSession.id
        } else {
            buildSessionId(databaseName, now)
        }
    }

    private fun appendHistoryEntry(session: QuizSession) {
        val history = listHistory().toMutableList()
        val existingEntry = history.firstOrNull { it.id == session.id }
        history.removeAll { it.id == session.id }
        history.add(
            session.copy(
                entryName = existingEntry?.entryName.orEmpty(),
            ),
        )
        saveHistoryList(history)
    }

    private fun saveHistoryList(history: List<QuizSession>) {
        val bounded = history
            .map { it.normalized() }
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(MAX_HISTORY_ENTRIES)
        FileSystemHelper.writeText(historyFile, json.encodeToString(bounded))
    }

    private fun deleteHistoryEntriesStrict(entryIds: Set<String>) {
        val updated = listHistory().filterNot { it.id in entryIds }
        saveHistoryList(updated)
        if (restoreSession()?.id in entryIds) {
            clearSession()
        }
    }

    private fun writeSession(session: QuizSession) {
        FileSystemHelper.writeText(sessionFile, json.encodeToString(session.normalized()))
    }

    private fun QuizSession.normalized(): QuizSession = copy(
        selectedSubjectIds = selectedSubjectIds.distinct().sorted(),
        selectedSystemIds = selectedSystemIds.distinct().sorted(),
        entryName = entryName.trim(),
        currentQuestionIndex = currentQuestionIndex.coerceAtLeast(0),
    )

    private fun buildSessionId(databaseName: String, now: Long): String = "$databaseName-$now"

    private fun handleReadError(path: String, context: ReadContext, throwable: Throwable) {
        if (throwable is SerializationException) {
            Logger.e("QuizSession", "Invalid ${context.label} payload, clearing corrupted file", throwable)
            runCatching { FileSystemHelper.delete(path) }
                .onFailure { Logger.e("QuizSession", "Failed to delete corrupted ${context.label} file", it) }
            return
        }
        Logger.e("QuizSession", "Error reading ${context.label}", throwable)
    }

    private enum class ReadContext(val label: String) {
        Session("session"),
        History("history"),
    }

    @Serializable
    data class QuizSession(
        val id: String = "",
        val databaseName: String,
        val entryName: String = "",
        val selectedSubjectIds: List<Long>,
        val selectedSystemIds: List<Long>,
        val performanceFilter: PerformanceFilter,
        val currentQuestionIndex: Int,
        val updatedAtEpochMillis: Long = 0,
        @EncodeDefault val isLoggingEnabled: Boolean = false,
    )

    companion object {
        private const val MAX_HISTORY_ENTRIES = 20
    }
}
