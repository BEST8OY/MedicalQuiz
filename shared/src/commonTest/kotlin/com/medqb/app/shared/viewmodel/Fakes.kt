package com.medqb.app.shared.viewmodel

import androidx.room3.withWriteTransaction
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MarkedQuestionsRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.orchestration.AppStartupCoordinator
import com.medqb.app.shared.orchestration.DatabaseSelectionDecision
import com.medqb.app.shared.data.database.QuestionDetails
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.domain.SnackbarSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FakeSettingsRepository(
    showMetadata: Boolean = true,
    fontScalePreference: Float? = null,
    isLoggingEnabled: Boolean = false,
    submissionMode: SubmissionMode = SubmissionMode.INSTANT,
) : SettingsRepository {
    override val showMetadata = MutableStateFlow(showMetadata)
    override val fontScalePreference = MutableStateFlow(fontScalePreference)
    override val isLoggingEnabled = MutableStateFlow(isLoggingEnabled)
    override val submissionMode = MutableStateFlow(submissionMode)

    var loggingToggles = 0

    override fun setShowMetadata(enabled: Boolean) {
        showMetadata.value = enabled
    }

    override fun setFontScalePreference(scale: Float?) {
        fontScalePreference.value = scale
    }

    override fun setLoggingEnabled(enabled: Boolean) {
        loggingToggles++
        isLoggingEnabled.value = enabled
    }

    override fun setSubmissionMode(mode: SubmissionMode) {
        submissionMode.value = mode
    }
}

/**
 * In-memory [TextHighlightsRepository]. Backing store keyed by (dbName, questionId);
 * seed via [seedHighlights] before the question loads. [addGate] can park add
 * operations to simulate slow disk I/O.
 */
class FakeTextHighlightsRepository : TextHighlightsRepository {
    private val store = mutableMapOf<Pair<String, Long>, MutableList<TextHighlight>>()
    private var nextId = 1L

    /** When set, [addHighlight] blocks until released — simulates slow disk I/O. */
    var addGate: CompletableDeferred<Unit>? = null

    fun seedHighlights(dbName: String, questionId: Long, highlights: List<TextHighlight>) {
        store.getOrPut(dbName to questionId) { mutableListOf() }.addAll(highlights)
        nextId = maxOf(nextId, highlights.maxOfOrNull { it.id }?.plus(1) ?: 1L)
    }

    fun highlightsFor(dbName: String, questionId: Long): List<TextHighlight> =
        store[dbName to questionId].orEmpty().toList()

    override suspend fun getHighlightsForQuestion(
        dbName: String,
        questionId: Long,
    ): List<TextHighlight> = highlightsFor(dbName, questionId)

    override suspend fun addHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        blockKey: String,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor,
    ): List<TextHighlight> {
        addGate?.await()
        val highlight = TextHighlight(
            id = nextId++,
            dbName = dbName,
            questionId = questionId,
            section = section,
            blockKey = blockKey,
            startOffset = startOffset,
            endOffset = endOffset,
            highlightedText = highlightedText,
            color = color,
            createdAt = 0L,
        )
        store.getOrPut(dbName to questionId) { mutableListOf() }.add(highlight)
        return highlightsFor(dbName, questionId)
    }

    override suspend fun removeHighlight(
        dbName: String,
        questionId: Long,
        highlightId: Long,
    ): List<TextHighlight> {
        store.getOrPut(dbName to questionId) { mutableListOf() }
            .removeAll { it.id == highlightId }
        return highlightsFor(dbName, questionId)
    }

    override suspend fun updateHighlightColor(
        dbName: String,
        questionId: Long,
        highlightId: Long,
        color: HighlightColor,
    ): List<TextHighlight> {
        store.getOrPut(dbName to questionId) { mutableListOf() }.replaceAll {
            if (it.id == highlightId) it.copy(color = color) else it
        }
        return highlightsFor(dbName, questionId)
    }
}

class FakeMarkedQuestionsRepository : MarkedQuestionsRepository {
    private val marksByDb = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    var setGate: CompletableDeferred<Unit>? = null
    var failWrites: Boolean = false
    var failReads: Boolean = false

    private fun canonical(dbName: String) = com.medqb.app.shared.data.canonicalizeDbName(dbName)

    fun seedMarked(dbName: String, vararg questionIds: Long) {
        val c = canonical(dbName)
        marksByDb.update { current ->
            current + (c to (current[c].orEmpty() + questionIds.toSet()))
        }
    }

    fun markedIdsFor(dbName: String): Set<Long> {
        val c = canonical(dbName)
        return marksByDb.value[c].orEmpty()
    }

    override suspend fun isMarked(dbName: String, questionId: Long): Boolean {
        if (failReads) throw RuntimeException("Simulated read failure")
        val c = canonical(dbName)
        return marksByDb.value[c]?.contains(questionId) == true
    }

    override suspend fun getMarkedQuestionIds(dbName: String): Set<Long> {
        if (failReads) throw RuntimeException("Simulated read failure")
        val c = canonical(dbName)
        return marksByDb.value[c].orEmpty()
    }

    override fun observeMarkedQuestionIds(dbName: String): Flow<Set<Long>> {
        val c = canonical(dbName)
        return marksByDb.map { it[c].orEmpty() }
    }

    override suspend fun setMarked(dbName: String, questionId: Long, marked: Boolean): Boolean {
        setGate?.await()
        if (failWrites) throw RuntimeException("Simulated write failure")
        val c = canonical(dbName)
        marksByDb.update { current ->
            val set = current[c].orEmpty()
            val newSet = if (marked) set + questionId else set - questionId
            current + (c to newSet)
        }
        return marked
    }

    override suspend fun toggleMark(dbName: String, questionId: Long): Boolean {
        setGate?.await()
        if (failWrites) throw RuntimeException("Simulated write failure")
        val c = canonical(dbName)
        val currentlyMarked = isMarked(c, questionId)
        val target = !currentlyMarked
        setMarked(c, questionId, target)
        return target
    }

    override suspend fun clearForDatabase(dbName: String) {
        val c = canonical(dbName)
        marksByDb.update { current ->
            current - c
        }
    }
}

class FakeQuizSessionRepository : QuizSessionRepository {
    override val historyEntries = MutableStateFlow<List<QuizSessionRepository.QuizSession>>(emptyList())
    var nextSessionId = "session-1"
    private var generatedCounter = 0
    val appended = mutableListOf<String>()
    val appendedCalls = mutableListOf<QuizSessionRepository.QuizSession>()
    var appendGate: CompletableDeferred<Unit>? = null
    var lastAppendedSubjectIds: Set<Long> = emptySet()
    var lastAppendedExcludedSubjectIds: Set<Long> = emptySet()
    var lastAppendedSystemIds: Set<Long> = emptySet()
    var lastAppendedExcludedSystemIds: Set<Long> = emptySet()
    var lastAppendedStatusFilter: StatusFilter = StatusFilter.ALL
    var lastAppendedDifficultyTiers: Set<DifficultyTier> = emptySet()
    var lastAppendedSessionId: String = ""
    var lastAppendedQuestionId: Long? = null
    var lastAppendedQuestionIndex: Int = -1
    var lastAppendedIsLoggingEnabled: Boolean = false
    var lastAppendedSubmissionMode: SubmissionMode = SubmissionMode.INSTANT
    val restorableEntries = mutableMapOf<String, QuizSessionRepository.QuizSession>()

    override fun generateSessionId(databaseName: String): String {
        return if (generatedCounter++ == 0) nextSessionId else "$nextSessionId-$generatedCounter"
    }

    override suspend fun listHistory() = emptyList<QuizSessionRepository.QuizSession>()

    var enqueueScope: CoroutineScope? = null

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
        val scope = enqueueScope
        if (scope != null) {
            scope.launch {
                appendToHistory(
                    databaseName = databaseName,
                    selectedSubjectIds = selectedSubjectIds,
                    excludedSubjectIds = excludedSubjectIds,
                    selectedSystemIds = selectedSystemIds,
                    excludedSystemIds = excludedSystemIds,
                    statusFilter = statusFilter,
                    currentQuestionIndex = currentQuestionIndex,
                    currentQuestionId = currentQuestionId,
                    isLoggingEnabled = isLoggingEnabled,
                    submissionMode = submissionMode,
                    currentSessionId = currentSessionId,
                    entryName = entryName,
                    selectedDifficultyTiers = selectedDifficultyTiers,
                )
            }
        } else {
            val canonicalDbName = com.medqb.app.shared.data.canonicalizeDbName(databaseName)
            if (canonicalDbName.isBlank() || currentSessionId.isBlank()) return
            recordAppend(
                databaseName = canonicalDbName,
                selectedSubjectIds = selectedSubjectIds,
                excludedSubjectIds = excludedSubjectIds,
                selectedSystemIds = selectedSystemIds,
                excludedSystemIds = excludedSystemIds,
                statusFilter = statusFilter,
                currentQuestionIndex = currentQuestionIndex,
                currentQuestionId = currentQuestionId,
                isLoggingEnabled = isLoggingEnabled,
                submissionMode = submissionMode,
                resolvedId = currentSessionId,
                entryName = entryName,
                selectedDifficultyTiers = selectedDifficultyTiers,
            )
        }
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
        appendGate?.await()
        val resolvedId = currentSessionId.ifBlank { generateSessionId(databaseName) }
        recordAppend(
            databaseName = databaseName,
            selectedSubjectIds = selectedSubjectIds,
            excludedSubjectIds = excludedSubjectIds,
            selectedSystemIds = selectedSystemIds,
            excludedSystemIds = excludedSystemIds,
            statusFilter = statusFilter,
            currentQuestionIndex = currentQuestionIndex,
            currentQuestionId = currentQuestionId,
            isLoggingEnabled = isLoggingEnabled,
            submissionMode = submissionMode,
            resolvedId = resolvedId,
            entryName = entryName,
            selectedDifficultyTiers = selectedDifficultyTiers,
        )
        return resolvedId
    }

    private fun recordAppend(
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
        resolvedId: String,
        entryName: String,
        selectedDifficultyTiers: Set<DifficultyTier>,
    ) {
        appended += databaseName
        lastAppendedSubjectIds = selectedSubjectIds
        lastAppendedExcludedSubjectIds = excludedSubjectIds
        lastAppendedSystemIds = selectedSystemIds
        lastAppendedExcludedSystemIds = excludedSystemIds
        lastAppendedStatusFilter = statusFilter
        lastAppendedDifficultyTiers = selectedDifficultyTiers
        lastAppendedSessionId = resolvedId
        lastAppendedQuestionId = currentQuestionId
        lastAppendedQuestionIndex = currentQuestionIndex
        lastAppendedIsLoggingEnabled = isLoggingEnabled
        lastAppendedSubmissionMode = submissionMode
        appendedCalls += QuizSessionRepository.QuizSession(
            id = resolvedId,
            databaseName = databaseName,
            entryName = entryName,
            selectedSubjectIds = selectedSubjectIds.toList(),
            excludedSubjectIds = excludedSubjectIds.toList(),
            selectedSystemIds = selectedSystemIds.toList(),
            excludedSystemIds = excludedSystemIds.toList(),
            statusFilter = statusFilter,
            currentQuestionIndex = currentQuestionIndex,
            currentQuestionId = currentQuestionId,
            isLoggingEnabled = isLoggingEnabled,
            submissionMode = submissionMode,
            selectedDifficultyTiers = selectedDifficultyTiers,
        )
    }

    override suspend fun deleteHistoryEntries(entryIds: Set<String>) = Unit

    override suspend fun renameHistoryEntry(entryId: String, newName: String) = Unit

    override suspend fun restoreDeletedHistoryEntry(entry: QuizSessionRepository.QuizSession) = Unit

    override suspend fun restoreHistoryEntry(entryId: String): QuizSessionRepository.QuizSession? =
        restorableEntries[entryId]
}

class FakeSnackbarSink : SnackbarSink {
    val messages = mutableListOf<SnackbarMessage>()

    override suspend fun emitSnackbar(message: SnackbarMessage) {
        messages += message
    }
}

/**
 * In-memory [DatabaseProvider]. Question ids are always [1, 2]; q1's correct
 * answer id is 101 (wrong: 102), q2 carries seeded performance stats.
 */
class FakeDatabaseProvider(
    private val dbName: String = "bank",
    private val q2Performance: QuestionPerformance? = null,
) : DatabaseProvider {
    data class LoggedAnswer(
        val qid: Long,
        val selectedAnswer: Int,
        val corrAnswer: Int,
        val time: Long,
        val sessionId: String,
    )

    private val questions = mapOf(
        1L to Question(
            id = 1,
            question = "<p>Q1</p>",
            explanation = "",
            corrAns = 2,
            title = null,
            mediaName = null,
            otherMedias = null,
            pplTaken = null,
            corrTaken = null,
            subId = null,
            sysId = null,
        ),
        2L to Question(
            id = 2,
            question = "<p>Q2</p>",
            explanation = "",
            corrAns = 1,
            title = null,
            mediaName = null,
            otherMedias = null,
            pplTaken = null,
            corrTaken = null,
            subId = null,
            sysId = null,
        ),
    )

    private val answers = mapOf(
        1L to listOf(
            Answer(answerId = 100, answerText = "a", correctPercentage = null, qId = 1),
            Answer(answerId = 101, answerText = "b", correctPercentage = null, qId = 1),
            Answer(answerId = 102, answerText = "c", correctPercentage = null, qId = 1),
        ),
        2L to listOf(
            Answer(answerId = 200, answerText = "a", correctPercentage = null, qId = 2),
            Answer(answerId = 201, answerText = "b", correctPercentage = null, qId = 2),
        ),
    )

    val loggedAnswers = mutableListOf<LoggedAnswer>()
    val clearedLogQids = mutableListOf<Long>()

    /** When set, q1 carries no answers — corrAns points nowhere, so its derived
     * correctAnswerId is null and the question becomes ungradable. */
    var breakQ1AnswerKey = false

    /** When set, [logAnswer] blocks until released — simulates slow disk I/O. */
    var logGate: CompletableDeferred<Unit>? = null
    var failLogAnswer = false

    private fun detailsFor(id: Long): QuestionDetails? {
        val question = questions[id] ?: return null
        val performance = if (id == 2L) q2Performance else null
        val answerList = if (id == 1L && breakQ1AnswerKey) emptyList() else answers.getValue(id)
        return QuestionDetails.from(question, answerList, performance)
    }

    override suspend fun closeDatabase() = Unit

    var questionIdsProvider: ((List<Long>?, List<Long>?, List<Long>?, List<Long>?, StatusFilter, Set<DifficultyTier>) -> List<Long>)? = null

    override suspend fun getQuestionIds(
        subjectIds: List<Long>?,
        excludedSubjectIds: List<Long>?,
        systemIds: List<Long>?,
        excludedSystemIds: List<Long>?,
        statusFilter: StatusFilter,
        difficultyFilters: Set<DifficultyTier>,
    ): List<Long> = questionIdsProvider?.invoke(subjectIds, excludedSubjectIds, systemIds, excludedSystemIds, statusFilter, difficultyFilters)
        ?: listOf(1L, 2L)

    override suspend fun getQuestionById(id: Long) = detailsFor(id)?.question

    override suspend fun getAnswersForQuestion(questionId: Long) =
        detailsFor(questionId)?.answers ?: emptyList()

    override suspend fun getQuestionWithDetails(
        questionId: Long,
        loadPerformance: Boolean,
    ): QuestionDetails {
        val details = detailsFor(questionId) ?: QuestionDetails(null, emptyList(), null, null)
        return if (loadPerformance) details else details.copy(performance = null)
    }

    var countQuestionIdsProvider: ((List<Long>?, List<Long>?, List<Long>?, List<Long>?, StatusFilter, Set<DifficultyTier>) -> Int)? = null

    override suspend fun countQuestionIds(
        subjectIds: List<Long>?,
        excludedSubjectIds: List<Long>?,
        systemIds: List<Long>?,
        excludedSystemIds: List<Long>?,
        statusFilter: StatusFilter,
        difficultyFilters: Set<DifficultyTier>,
    ): Int = countQuestionIdsProvider?.invoke(subjectIds, excludedSubjectIds, systemIds, excludedSystemIds, statusFilter, difficultyFilters)
        ?: if (statusFilter.isAll) 10 else 3

    var difficultyAvailable: Boolean = false
    var difficultyCountsProvider: ((List<Long>?, List<Long>?, List<Long>?, List<Long>?, StatusFilter) -> Map<DifficultyTier, Int>)? = null

    override fun isDifficultyAvailable(): Boolean = difficultyAvailable

    override suspend fun getDifficultyCounts(
        subjectIds: List<Long>?,
        excludedSubjectIds: List<Long>?,
        systemIds: List<Long>?,
        excludedSystemIds: List<Long>?,
        statusFilter: StatusFilter,
    ): Map<DifficultyTier, Int> =
        difficultyCountsProvider?.invoke(subjectIds, excludedSubjectIds, systemIds, excludedSystemIds, statusFilter) ?: emptyMap()

    var seededSubjects: List<Subject> = emptyList()
    var seededSystems: List<System> = emptyList()

    override suspend fun getSubjects(): List<Subject> = seededSubjects

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = seededSystems

    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String,
    ) {
        logGate?.await()
        if (failLogAnswer) throw RuntimeException("disk boom")
        loggedAnswers += LoggedAnswer(qid, selectedAnswer, corrAnswer, time, sessionId)
    }

    override suspend fun clearLogForQuestion(qid: Long) {
        clearedLogQids += qid
    }

    override suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? =
        if (qid == 2L) {
            QuestionPerformance(
                qid = 2,
                lastCorrect = true,
                everCorrect = true,
                everIncorrect = false,
                attempts = 7,
                correctCount = 7,
                incorrectCount = 0,
            )
        } else {
            null
        }

    suspend fun installInto(holder: ActiveDatabaseHolder) {
        holder.setDatabase(dbName, this)
    }
}

open class FakeUserDataManager(
    private var db: UserDatabase? = null,
) : UserDataManager() {
    override suspend fun init() {
        if (db != null) setDbState(DbState.Open(nextOpenEpoch()))
    }
    fun swapDatabase(newDb: UserDatabase?) {
        db = newDb
        setDbState(if (newDb != null) DbState.Open(nextOpenEpoch()) else DbState.Closed)
    }
    override suspend fun logDao() = db?.logDao() ?: throw IllegalStateException("No test DB")
    override suspend fun sessionHistoryDao() = db?.sessionHistoryDao() ?: throw IllegalStateException("No test DB")
    override suspend fun textHighlightDao() = db?.textHighlightDao() ?: throw IllegalStateException("No test DB")
    override suspend fun markedQuestionDao() = db?.markedQuestionDao() ?: throw IllegalStateException("No test DB")
    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        db?.withWriteTransaction { block() } ?: block()
    override suspend fun close() {
        db?.close()
        db = null
        setDbState(DbState.Closed)
    }
}

class FakeAppStartupCoordinator(
    var availableDatabasesResult: List<String> = listOf("Cardio.db", "Renal.db"),
    var shouldFail: Boolean = false,
) : AppStartupCoordinator(
    LocalContentRepository(),
    ActiveDatabaseHolder(),
    FakeUserDataManager(),
) {
    var initializedCount = 0
    var refreshCount = 0
    var lastSelectedDb: String? = null

    override suspend fun initializeApp(): List<String> {
        initializedCount++
        if (shouldFail) throw RuntimeException("Failed to scan databases")
        return availableDatabasesResult
    }

    override suspend fun refreshDatabases(): List<String> {
        refreshCount++
        if (shouldFail) throw RuntimeException("Failed to refresh")
        return availableDatabasesResult
    }

    override suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
        userDataManager: UserDataManager,
    ): DatabaseSelectionDecision? {
        lastSelectedDb = selectedDatabase
        return selectedDatabase?.let { DatabaseSelectionDecision(it) }
    }
}

