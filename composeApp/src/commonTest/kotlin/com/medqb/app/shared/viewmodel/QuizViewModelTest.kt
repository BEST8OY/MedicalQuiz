package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runQuizTest(testBody: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher(scheduler)) {
        testBody()
    }

    private fun createViewModel(
        provider: FakeDatabaseProvider,
        holder: ActiveDatabaseHolder,
        sessionRepository: FakeQuizSessionRepository = FakeQuizSessionRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(isLoggingEnabled = true),
        snackbar: FakeSnackbarSink = FakeSnackbarSink(),
        highlights: FakeTextHighlightsRepository = FakeTextHighlightsRepository(),
    ): QuizViewModel {
        return QuizViewModel(
            settingsRepository = settings,
            textHighlightsRepository = highlights,
            sessionRepository = sessionRepository,
            savedStateHandle = SavedStateHandle(),
            activeDatabaseHolder = holder,
            loadQuestionUseCase = LoadQuestionUseCase(highlights),
            snackbarSink = snackbar,
            filterStateHolder = FilterStateHolder(),
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
    }

    @Test
    fun databaseSelectionLoadsFirstQuestionWithDerivedCorrectAnswer() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("bank", state.databaseName)
        assertEquals(listOf(1L, 2L), state.questionIds)
        assertEquals(1L, state.currentQuestion?.id)
        // corrAns=2 (1-based index into ORDER BY id answers) -> answerId 101,
        // derived once during load and exposed through ui state.
        assertEquals(101, state.correctAnswerId)
    }

    @Test
    fun submitAnswerLogsDerivedCorrectAnswerAndUpdatesPerformance() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.onAnswerSelected(101) // correct for q1
        viewModel.submitAnswer(timeTaken = 5)
        advanceUntilIdle()

        val logged = provider.loggedAnswers.single()
        assertEquals(1L, logged.qid)
        assertEquals(101, logged.selectedAnswer)
        assertEquals(101, logged.corrAnswer)
        assertTrue(viewModel.state.value.answerSubmitted)

        val performance = viewModel.state.value.currentPerformance
        assertEquals(1, performance?.attempts)
        assertEquals(1, performance?.correctCount)
    }

    @Test
    fun submitWithoutSelectionShowsPromptAndDoesNotLog() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val snackbar = FakeSnackbarSink()
        val viewModel = createViewModel(provider, holder, snackbar = snackbar)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.submitAnswer(timeTaken = 5)
        advanceUntilIdle()

        assertTrue(provider.loggedAnswers.isEmpty())
        assertTrue(snackbar.messages.any { (it as? SnackbarMessage.Simple)?.message == "Please select an answer" })
        assertFalse(viewModel.state.value.answerSubmitted)
    }

    @Test
    fun submitAnswerFailureResetsSubmissionAndEmitsSnackbar() = runQuizTest {
        val provider = FakeDatabaseProvider().apply { failLogAnswer = true }
        val holder = ActiveDatabaseHolder()
        val snackbar = FakeSnackbarSink()
        val viewModel = createViewModel(provider, holder, snackbar = snackbar)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.onAnswerSelected(102)
        viewModel.submitAnswer(timeTaken = 5)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.answerSubmitted)
        assertTrue(snackbar.messages.any {
            it is SnackbarMessage.Simple && it.message.startsWith("Error saving answer")
        })
        assertTrue(provider.loggedAnswers.isEmpty())
    }

    @Test
    fun slowAnswerLogDoesNotClobberNextQuestionsPerformance() = runQuizTest {
        val provider = FakeDatabaseProvider(
            q2Performance = QuestionPerformance(
                qid = 2,
                lastCorrect = true,
                everCorrect = true,
                everIncorrect = false,
                attempts = 7,
                correctCount = 7,
                incorrectCount = 0,
            )
        )
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        provider.logGate = gate
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        // Submit an incorrect answer for q1; the write parks on the gate.
        viewModel.onAnswerSelected(102)
        viewModel.submitAnswer(timeTaken = 5)
        runCurrent()

        // Navigate to q2 while the write is still in flight.
        viewModel.loadNext()
        advanceUntilIdle()
        assertEquals(2L, viewModel.state.value.currentQuestion?.id)
        assertEquals(7, viewModel.state.value.currentPerformance?.attempts)

        // Release the parked write; the stale merge attempt must be skipped.
        gate.complete(Unit)
        advanceUntilIdle()

        val logged = provider.loggedAnswers.single()
        assertEquals(1L, logged.qid)
        assertEquals(102, logged.selectedAnswer)
        // q2's stats are untouched by q1's late result.
        assertEquals(7, viewModel.state.value.currentPerformance?.attempts)
        assertEquals(7, viewModel.state.value.currentPerformance?.correctCount)
    }

    @Test
    fun navigationBoundsAreRespected() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.loadPrevious() // already at first question
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.currentQuestionIndex)

        viewModel.loadNext()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentQuestionIndex)
        assertEquals(2L, viewModel.state.value.currentQuestion?.id)
        assertEquals(200, viewModel.state.value.correctAnswerId)

        viewModel.loadNext() // past the end
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentQuestionIndex)
    }

    @Test
    fun questionLoadPublishesHighlightsAtomicallyWithQuestionText() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val highlights = FakeTextHighlightsRepository().apply {
            seedHighlights("bank", 1L, listOf(highlight(id = 11, qid = 1, section = HighlightSection.QUESTION)))
            seedHighlights("bank", 2L, listOf(highlight(id = 22, qid = 2, section = HighlightSection.EXPLANATION)))
        }
        val viewModel = createViewModel(provider, holder, highlights = highlights)
        provider.installInto(holder)
        advanceUntilIdle()

        // q1's highlights arrive in the same state frame as q1's text...
        assertEquals(1L, viewModel.state.value.currentQuestion?.id)
        assertEquals(listOf(11L), viewModel.state.value.questionHighlights.map { it.id })
        assertTrue(viewModel.state.value.explanationHighlights.isEmpty())

        // ...and navigating swaps both together — no cross-question contamination.
        viewModel.loadNext()
        advanceUntilIdle()
        assertEquals(2L, viewModel.state.value.currentQuestion?.id)
        assertTrue(viewModel.state.value.questionHighlights.isEmpty())
        assertEquals(listOf(22L), viewModel.state.value.explanationHighlights.map { it.id })
    }

    @Test
    fun highlightMutationsRoundTripThroughUiState() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.addHighlight(HighlightSection.QUESTION, startOffset = 0, endOffset = 4, highlightedText = "<p>", color = HighlightColor.GREEN)
        advanceUntilIdle()

        val added = viewModel.state.value.questionHighlights.single()
        assertEquals(0, added.startOffset)
        assertEquals(4, added.endOffset)
        assertEquals(HighlightColor.GREEN, added.color)

        viewModel.changeHighlightColor(added.id, HighlightColor.BLUE)
        advanceUntilIdle()
        assertEquals(HighlightColor.BLUE, viewModel.state.value.questionHighlights.single().color)

        viewModel.removeHighlight(added.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.questionHighlights.isEmpty())
    }

    @Test
    fun staleHighlightMutationDoesNotPublishIntoNextQuestion() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val highlights = FakeTextHighlightsRepository()
        val viewModel = createViewModel(provider, holder, highlights = highlights)
        provider.installInto(holder)
        advanceUntilIdle()

        // Park an add for q1 on a gate — the write is in flight.
        val gate = CompletableDeferred<Unit>()
        highlights.addGate = gate
        viewModel.addHighlight(HighlightSection.QUESTION, 0, 2, "Q1", HighlightColor.GREEN)
        runCurrent()

        // Navigate to q2 while the write is still parked.
        viewModel.loadNext()
        advanceUntilIdle()
        assertEquals(2L, viewModel.state.value.currentQuestion?.id)

        // Release the parked write; it lands for q1 but must not leak into q2's state.
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.questionHighlights.isEmpty())
        assertEquals(1, highlights.highlightsFor("bank", 1L).size)
    }

    @Test
    fun submissionOnUngradableQuestionSkipsLoggingAndExplains() = runQuizTest {
        // corrAns points nowhere — the question cannot be graded.
        val provider = FakeDatabaseProvider().apply { breakQ1AnswerKey = true }
        val holder = ActiveDatabaseHolder()
        val snackbar = FakeSnackbarSink()
        val viewModel = createViewModel(provider, holder, snackbar = snackbar)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.onAnswerSelected(100)
        viewModel.submitAnswer(timeTaken = 5)
        advanceUntilIdle()

        // No poisoned log row, no recorded performance…
        assertTrue(provider.loggedAnswers.isEmpty())
        assertNull(viewModel.state.value.currentPerformance)
        // …but the UI flow still completes and explains why nothing was saved.
        assertTrue(viewModel.state.value.answerSubmitted)
        assertTrue(snackbar.messages.any {
            it is SnackbarMessage.Simple && it.message.contains("no valid answer key")
        })
    }

    private fun highlight(id: Long, qid: Long, section: HighlightSection) = TextHighlight(
        id = id,
        dbName = "bank",
        questionId = qid,
        section = section,
        startOffset = 0,
        endOffset = 3,
        highlightedText = "abc",
        color = HighlightColor.YELLOW,
        createdAt = 0L,
    )

    @Test
    fun clearingLogClearsDisplayedPerformanceAndConfirmsViaSnackbar() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val snackbar = FakeSnackbarSink()
        val viewModel = createViewModel(provider, holder, snackbar = snackbar)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.onAnswerSelected(101)
        viewModel.submitAnswer(timeTaken = 5)
        advanceUntilIdle()

        viewModel.clearCurrentQuestionLog()
        advanceUntilIdle()

        assertNull(viewModel.state.value.currentPerformance)
        assertTrue(snackbar.messages.any {
            it is SnackbarMessage.Simple && it.message.contains("Log cleared")
        })
    }

    @Test
    fun submissionModeRestoredFromSettingsWhenNotSaved() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = QuizViewModel(
            settingsRepository = FakeSettingsRepository(submissionMode = SubmissionMode.MANUAL),
            textHighlightsRepository = FakeTextHighlightsRepository(),
            sessionRepository = FakeQuizSessionRepository(),
            savedStateHandle = SavedStateHandle(),
            activeDatabaseHolder = holder,
            loadQuestionUseCase = LoadQuestionUseCase(FakeTextHighlightsRepository()),
            snackbarSink = FakeSnackbarSink(),
            filterStateHolder = FilterStateHolder(),
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals(SubmissionMode.MANUAL, viewModel.state.value.submissionMode)
    }

    @Test
    fun runtimeSettingsChangeUpdatesQuizStateDynamically() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val settings = FakeSettingsRepository(
            submissionMode = SubmissionMode.INSTANT,
            isLoggingEnabled = true,
        )
        val viewModel = createViewModel(provider, holder, settings = settings)
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals(SubmissionMode.INSTANT, viewModel.state.value.submissionMode)
        assertTrue(viewModel.state.value.isLoggingEnabled)

        settings.setSubmissionMode(SubmissionMode.MANUAL)
        settings.setLoggingEnabled(false)
        advanceUntilIdle()

        assertEquals(SubmissionMode.MANUAL, viewModel.state.value.submissionMode)
        assertFalse(viewModel.state.value.isLoggingEnabled)
    }

    @Test
    fun toolbarTitleReflectsEntryNameOrDatabaseName() = runQuizTest {
        val provider = FakeDatabaseProvider(dbName = "Cardiology.db")
        val holder = ActiveDatabaseHolder()
        val savedStateHandle = SavedStateHandle(mapOf("entryName" to "My Custom Quiz"))
        val viewModel = QuizViewModel(
            settingsRepository = FakeSettingsRepository(),
            textHighlightsRepository = FakeTextHighlightsRepository(),
            sessionRepository = FakeQuizSessionRepository(),
            savedStateHandle = savedStateHandle,
            activeDatabaseHolder = holder,
            loadQuestionUseCase = LoadQuestionUseCase(FakeTextHighlightsRepository()),
            snackbarSink = FakeSnackbarSink(),
            filterStateHolder = FilterStateHolder(),
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
        // Immediately upon construction without advanceUntilIdle, toolbarTitle.value must be synchronous
        assertEquals("My Custom Quiz", viewModel.toolbarTitle.value)
        assertEquals("My Custom Quiz", viewModel.state.value.toolbarTitle)

        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals("My Custom Quiz", viewModel.state.value.toolbarTitle)
        assertEquals("My Custom Quiz", viewModel.toolbarTitle.value)
    }

    @Test
    fun restoredHistorySessionPreservesCustomSettingsOverGlobalChanges() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val settings = FakeSettingsRepository(
            submissionMode = SubmissionMode.INSTANT,
            isLoggingEnabled = true,
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to "history_session_123",
                "isLoggingEnabled" to false,
                "submissionMode" to SubmissionMode.MANUAL.name,
            )
        )
        val viewModel = QuizViewModel(
            settingsRepository = settings,
            textHighlightsRepository = FakeTextHighlightsRepository(),
            sessionRepository = FakeQuizSessionRepository(),
            savedStateHandle = savedStateHandle,
            activeDatabaseHolder = holder,
            loadQuestionUseCase = LoadQuestionUseCase(FakeTextHighlightsRepository()),
            snackbarSink = FakeSnackbarSink(),
            filterStateHolder = FilterStateHolder(),
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
        provider.installInto(holder)
        advanceUntilIdle()

        // Restored history session rules are preserved despite global settings being INSTANT / true
        assertEquals(SubmissionMode.MANUAL, viewModel.state.value.submissionMode)
        assertFalse(viewModel.state.value.isLoggingEnabled)

        // Global settings changing should not overwrite the restored history session rules
        settings.setSubmissionMode(SubmissionMode.INSTANT)
        settings.setLoggingEnabled(true)
        advanceUntilIdle()

        assertEquals(SubmissionMode.MANUAL, viewModel.state.value.submissionMode)
        assertFalse(viewModel.state.value.isLoggingEnabled)
    }

    @Test
    fun rapidConsecutiveQuestionLoadsCancelsInFlightAndLoadsLatest() = runQuizTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals(1L, viewModel.state.value.currentQuestion?.id)

        // Dispatch load for question 2 then immediately question 1
        viewModel.loadQuestion(1) // q2
        viewModel.loadQuestion(0) // q1
        advanceUntilIdle()

        // Final state reflects latest requested question (q1)
        assertEquals(0, viewModel.state.value.currentQuestionIndex)
        assertEquals(1L, viewModel.state.value.currentQuestion?.id)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun clearingLogDoesNotWipePerformanceWhenNavigatedToAnotherQuestion() = runQuizTest {
        val provider = FakeDatabaseProvider(
            q2Performance = QuestionPerformance(
                qid = 2,
                lastCorrect = true,
                everCorrect = true,
                everIncorrect = false,
                attempts = 7,
                correctCount = 7,
                incorrectCount = 0,
            )
        )
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        // Load question 2 (which has non-null performance in FakeDatabaseProvider)
        viewModel.loadQuestion(1)
        advanceUntilIdle()
        val q2Performance = viewModel.state.value.currentPerformance
        assertTrue(q2Performance != null && q2Performance.attempts == 7)

        // Clear log for question 2, but simulate user moving back to question 1
        // (Fake clearLog will complete while on question 1)
        viewModel.loadQuestion(0)
        advanceUntilIdle()
        assertNull(viewModel.state.value.currentPerformance) // Question 1 has null performance

        viewModel.clearCurrentQuestionLog()
        advanceUntilIdle()

        // Navigate back to question 2: its performance should be loaded fresh and intact
        viewModel.loadQuestion(1)
        advanceUntilIdle()
        val reloadedPerformance = viewModel.state.value.currentPerformance
        assertTrue(reloadedPerformance != null && reloadedPerformance.attempts == 7)
    }
}
