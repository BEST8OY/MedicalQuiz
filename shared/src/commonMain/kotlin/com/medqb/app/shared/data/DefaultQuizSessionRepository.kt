package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.local.entity.QuizHistoryEntity
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Room-backed [QuizSessionRepository] over the shared user database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@SingleIn(AppScope::class)
class DefaultQuizSessionRepository(
    private val userDataManager: UserDataManager,
) : QuizSessionRepository {
    // Process-scoped: intentionally not cancelled — survives config changes and screen pops
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface HistoryActorMessage {
        data class Write(
            val sessionId: String,
            val canonicalDbName: String,
            val selectedSubjectIds: Set<Long>,
            val excludedSubjectIds: Set<Long>,
            val selectedSystemIds: Set<Long>,
            val excludedSystemIds: Set<Long>,
            val statusFilter: StatusFilter,
            val currentQuestionIndex: Int,
            val currentQuestionId: Long?,
            val isLoggingEnabled: Boolean,
            val submissionMode: SubmissionMode,
            val entryName: String,
            val selectedDifficultyTiers: Set<DifficultyTier>,
        ) : HistoryActorMessage

        data class Flush(val ack: CompletableDeferred<Unit>) : HistoryActorMessage
    }

    private val historyWriteChannel = Channel<HistoryActorMessage>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in historyWriteChannel) {
                processActorBatch(first)
            }
        }
    }

    private suspend fun processActorBatch(first: HistoryActorMessage) {
        var currentWrite: HistoryActorMessage.Write? = when (first) {
            is HistoryActorMessage.Write -> first
            is HistoryActorMessage.Flush -> {
                first.ack.complete(Unit)
                return
            }
        }
        while (true) {
            val next = historyWriteChannel.tryReceive().getOrNull()
            when (next) {
                null -> break
                is HistoryActorMessage.Write -> {
                    val prev = currentWrite
                    if (prev != null &&
                        prev.sessionId == next.sessionId &&
                        prev.canonicalDbName == next.canonicalDbName
                    ) {
                        currentWrite = next
                    } else {
                        if (prev != null) executeWrite(prev)
                        currentWrite = next
                    }
                }
                is HistoryActorMessage.Flush -> {
                    currentWrite?.let { executeWrite(it) }
                    currentWrite = null
                    next.ack.complete(Unit)
                }
            }
        }
        currentWrite?.let { executeWrite(it) }
    }

    private suspend fun executeWrite(write: HistoryActorMessage.Write) {
        try {
            appendToHistory(
                databaseName = write.canonicalDbName,
                selectedSubjectIds = write.selectedSubjectIds,
                excludedSubjectIds = write.excludedSubjectIds,
                selectedSystemIds = write.selectedSystemIds,
                excludedSystemIds = write.excludedSystemIds,
                statusFilter = write.statusFilter,
                currentQuestionIndex = write.currentQuestionIndex,
                currentQuestionId = write.currentQuestionId,
                isLoggingEnabled = write.isLoggingEnabled,
                submissionMode = write.submissionMode,
                currentSessionId = write.sessionId,
                entryName = write.entryName,
                selectedDifficultyTiers = write.selectedDifficultyTiers,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("QuizSession", "Error executing queued history update", e)
        }
    }

    override val historyEntries: StateFlow<List<QuizSessionRepository.QuizSession>> =
        userDataManager.dbState
            .flatMapLatest { state ->
                when (state) {
                    UserDataManager.DbState.Closed -> flowOf(emptyList())
                    is UserDataManager.DbState.Open -> flow {
                        emitAll(userDataManager.sessionHistoryDao().listHistory())
                    }.catch { e ->
                        Logger.e("QuizSession", "Error in historyEntries flow", e)
                        emit(emptyList())
                    }
                }
            }
            .map { entities -> entities.map { it.toQuizSession() } }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    override fun enqueueHistoryUpdate(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long>,
        statusFilter: StatusFilter,
        currentQuestionIndex: Int,
        currentQuestionId: Long?,
        isLoggingEnabled: Boolean,
        submissionMode: SubmissionMode,
        currentSessionId: String,
        entryName: String,
        selectedDifficultyTiers: Set<DifficultyTier>,
    ) {
        val canonicalDbName = canonicalizeDbName(databaseName)
        if (canonicalDbName.isBlank() || currentSessionId.isBlank()) return
        historyWriteChannel.trySend(
            HistoryActorMessage.Write(
                sessionId = currentSessionId,
                canonicalDbName = canonicalDbName,
                selectedSubjectIds = selectedSubjectIds,
                excludedSubjectIds = excludedSubjectIds,
                selectedSystemIds = selectedSystemIds,
                excludedSystemIds = excludedSystemIds,
                statusFilter = statusFilter,
                currentQuestionIndex = currentQuestionIndex,
                currentQuestionId = currentQuestionId,
                isLoggingEnabled = isLoggingEnabled,
                submissionMode = submissionMode,
                entryName = entryName,
                selectedDifficultyTiers = selectedDifficultyTiers,
            )
        )
    }

    suspend fun flushPendingHistoryWrites() {
        val ack = CompletableDeferred<Unit>()
        historyWriteChannel.send(HistoryActorMessage.Flush(ack))
        ack.await()
    }

    override suspend fun listHistory(): List<QuizSessionRepository.QuizSession> =
        withContext(Dispatchers.IO) {
            flushPendingHistoryWrites()
            userDataManager.sessionHistoryDao().listHistoryOnce().map { it.toQuizSession() }
        }

    override suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long>,
        statusFilter: StatusFilter,
        currentQuestionIndex: Int,
        currentQuestionId: Long?,
        isLoggingEnabled: Boolean,
        submissionMode: SubmissionMode,
        currentSessionId: String,
        entryName: String,
        selectedDifficultyTiers: Set<DifficultyTier>,
    ): String {
        val canonicalDbName = canonicalizeDbName(databaseName)
        if (canonicalDbName.isBlank()) {
            Logger.e("QuizSession", "Cannot append history: databaseName is blank")
            return ""
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = if (currentSessionId.isNotBlank()) {
            currentSessionId
        } else {
            generateSessionId(canonicalDbName)
        }

        val entity = QuizHistoryEntity(
            sessionId = sessionId,
            databaseName = canonicalDbName,
            entryName = entryName,
            selectedSubjectIds = selectedSubjectIds.toSortedSet().joinToString(","),
            excludedSubjectIds = excludedSubjectIds.toSortedSet().joinToString(","),
            selectedSystemIds = selectedSystemIds.toSortedSet().joinToString(","),
            excludedSystemIds = excludedSystemIds.toSortedSet().joinToString(","),
            statusFilter = statusFilter.serialize(),
            currentQuestionIndex = currentQuestionIndex,
            currentQuestionId = currentQuestionId,
            updatedAt = now,
            isLoggingEnabled = isLoggingEnabled,
            submissionMode = submissionMode.name,
            selectedDifficultyTiers = selectedDifficultyTiers.map { it.name }.sorted().joinToString(","),
        )

        withContext(NonCancellable + Dispatchers.IO) {
            userDataManager.sessionHistoryDao().upsertHistory(entity)
        }

        return sessionId
    }

    override suspend fun deleteHistoryEntries(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isEmpty()) return@withContext
        flushPendingHistoryWrites()
        userDataManager.withTransaction {
            val dao = userDataManager.sessionHistoryDao()
            entryIds.chunked(500).forEach { batch ->
                dao.deleteHistory(batch)
            }
            dao.deleteOrphanedSessions()
        }
    }

    override suspend fun renameHistoryEntry(entryId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return@withContext
        flushPendingHistoryWrites()
        userDataManager.sessionHistoryDao().renameHistory(entryId, trimmedName)
    }

    override suspend fun restoreDeletedHistoryEntry(entry: QuizSessionRepository.QuizSession) {
        val canonicalDbName = canonicalizeDbName(entry.databaseName)
        if (entry.id.isBlank() || canonicalDbName.isBlank()) {
            Logger.e("QuizSession", "Cannot restore deleted history entry: id or databaseName is blank")
            return
        }
        flushPendingHistoryWrites()
        withContext(Dispatchers.IO) {
            userDataManager.sessionHistoryDao().upsertHistory(entry.toEntity())
        }
    }

    override suspend fun restoreHistoryEntry(entryId: String): QuizSessionRepository.QuizSession? =
        withContext(Dispatchers.IO) {
            flushPendingHistoryWrites()
            userDataManager.sessionHistoryDao().getHistory(entryId)?.toQuizSession()
        }
}

private fun QuizSessionRepository.QuizSession.toEntity() = QuizHistoryEntity(
    sessionId = id,
    databaseName = canonicalizeDbName(databaseName),
    entryName = entryName,
    selectedSubjectIds = selectedSubjectIds.toSortedSet().joinToString(","),
    excludedSubjectIds = excludedSubjectIds.toSortedSet().joinToString(","),
    selectedSystemIds = selectedSystemIds.toSortedSet().joinToString(","),
    excludedSystemIds = excludedSystemIds.toSortedSet().joinToString(","),
    statusFilter = statusFilter.serialize(),
    currentQuestionIndex = currentQuestionIndex,
    currentQuestionId = currentQuestionId,
    updatedAt = updatedAtEpochMillis,
    isLoggingEnabled = isLoggingEnabled,
    submissionMode = submissionMode.name,
    selectedDifficultyTiers = selectedDifficultyTiers.map { it.name }.sorted().joinToString(","),
)

private fun String.parseIdList(): List<Long> =
    if (isBlank()) emptyList()
    else split(",").mapNotNull { it.trim().trim('[', ']', ' ').toLongOrNull() }

private fun QuizHistoryEntity.toQuizSession() = QuizSessionRepository.QuizSession(
    id = sessionId,
    databaseName = canonicalizeDbName(databaseName),
    entryName = entryName,
    selectedSubjectIds = selectedSubjectIds.parseIdList(),
    excludedSubjectIds = excludedSubjectIds.parseIdList(),
    selectedSystemIds = selectedSystemIds.parseIdList(),
    excludedSystemIds = excludedSystemIds.parseIdList(),
    statusFilter = StatusFilter.deserialize(statusFilter),
    currentQuestionIndex = currentQuestionIndex,
    currentQuestionId = currentQuestionId,
    updatedAtEpochMillis = updatedAt,
    isLoggingEnabled = isLoggingEnabled,
    submissionMode = runCatching { SubmissionMode.valueOf(submissionMode) }.getOrDefault(SubmissionMode.INSTANT),
    selectedDifficultyTiers = if (selectedDifficultyTiers.isBlank()) {
        emptySet()
    } else {
        selectedDifficultyTiers.split(",")
            .mapNotNull { token ->
                token.trim().takeIf { it.isNotEmpty() }?.let { t ->
                    runCatching { DifficultyTier.valueOf(t) }.getOrNull()
                }
            }
            .toSet()
    },
)
