package com.medqb.app.shared.viewmodel

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.PerformanceFilter
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        store[dbName to questionId].orEmpty()

    override suspend fun getHighlightsForQuestion(
        dbName: String,
        questionId: Long,
    ): List<TextHighlight> = highlightsFor(dbName, questionId)

    override suspend fun addHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
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

class FakeQuizSessionRepository : QuizSessionRepository {
    override val historyEntries = MutableStateFlow<List<QuizSessionRepository.QuizSession>>(emptyList())
    var nextSessionId = "session-1"
    val appended = mutableListOf<String>()

    override suspend fun listHistory() = emptyList<QuizSessionRepository.QuizSession>()

    override suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        isLoggingEnabled: Boolean,
        submissionMode: SubmissionMode,
        currentSessionId: String,
        entryName: String,
    ): String {
        appended += databaseName
        return currentSessionId.ifBlank { nextSessionId }
    }

    override suspend fun deleteHistoryEntries(entryIds: Set<String>) = Unit

    override suspend fun renameHistoryEntry(entryId: String, newName: String) = Unit

    override suspend fun restoreDeletedHistoryEntry(entry: QuizSessionRepository.QuizSession) = Unit

    override suspend fun restoreHistoryEntry(entryId: String): QuizSessionRepository.QuizSession? = null
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

    override suspend fun getQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): List<Long> = listOf(1L, 2L)

    override suspend fun getQuestionById(id: Long) = detailsFor(id)?.question

    override suspend fun getAnswersForQuestion(questionId: Long) =
        detailsFor(questionId)?.answers ?: emptyList()

    override suspend fun getQuestionWithDetails(
        questionId: Long,
        loadPerformance: Boolean,
    ): QuestionDetails = detailsFor(questionId)
        ?: QuestionDetails(null, emptyList(), null, null)

    override suspend fun countQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int = if (performanceFilter == PerformanceFilter.ALL) 10 else 3

    override suspend fun getSubjects(): List<Subject> = emptyList()

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = emptyList()

    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String,
    ) {
        logGate?.await()
        if (failLogAnswer) throw RuntimeException("disk boom")
        loggedAnswers += LoggedAnswer(qid, selectedAnswer, corrAnswer, sessionId)
    }

    override suspend fun clearLogForQuestion(qid: Long) = Unit

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
