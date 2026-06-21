package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.navigation.QuizLaunchSource
import dev.zacsweers.metro.Inject

/**
 * Pure-logic coordinator that translates user actions and startup events into
 * [AppWorkflowState] mutations.
 *
 * The root `App` composable calls these methods and applies the returned state;
 * the coordinator never touches the back stack directly, keeping navigation
 * side-effects in the composable.
 */
@Inject
class AppWorkflowCoordinator(
    private val startupCoordinator: AppStartupCoordinator,
    private val filterStateHolder: FilterStateHolder,
) {

    // ── Bootstrap ────────────────────────────────────────────────────────

    /**
     * Builds the initial [AppWorkflowState] for a fresh start.
     */
    fun initialState(): AppWorkflowState {
        return AppWorkflowState()
    }

    // ── Database selection flow ──────────────────────────────────────────

    /**
     * Delegates to [AppStartupCoordinator] for the DB-init decision.
     */
    suspend fun handleDatabaseSelection(
        state: AppWorkflowState,
    ): DatabaseSelectionDecision? {
        val dbName = state.selectedDatabase ?: return null
        return startupCoordinator.handleDatabaseSelection(
            selectedDatabase = dbName,
            initializedDatabase = state.initializedDatabase,
            pendingLaunchSource = state.pendingLaunchSource,
        )
    }

    /**
     * Applies a [DatabaseSelectionDecision] to the workflow state.
     */
    fun applyDatabaseSelectionDecision(
        state: AppWorkflowState,
        decision: DatabaseSelectionDecision,
    ): AppWorkflowState {
        return state.copy(
            initializedDatabase = decision.initializedDatabase,
            pendingLaunchSource = decision.pendingLaunchSource,
        )
    }

    /**
     * Called when the user picks a database from the selection screen.
     */
    fun databaseSelected(state: AppWorkflowState, dbName: String): AppWorkflowState {
        filterStateHolder.reset()
        return state.copy(
            selectedDatabase = dbName,
            initializedDatabase = null,
            pendingLaunchSource = null,
            activeQuizLaunchSource = QuizLaunchSource.Standard,
        )
    }

    /**
     * Called when the user explicitly navigates back to database selection.
     */
    fun databaseSelectionRequested(state: AppWorkflowState): AppWorkflowState {
        return state.copy(
            selectedDatabase = null,
            initializedDatabase = null,
            activeQuizLaunchSource = QuizLaunchSource.Standard,
        )
    }

    // ── Quiz launch flow ─────────────────────────────────────────────────

    /**
     * Called when a history entry has been restored and the quiz screen
     * should open.
     */
    fun historyLaunchPrepared(
        state: AppWorkflowState,
        matchingDatabase: String,
    ): AppWorkflowState {
        return state.copy(
            selectedDatabase = matchingDatabase,
            pendingLaunchSource = QuizLaunchSource.History,
            activeQuizLaunchSource = QuizLaunchSource.History,
        )
    }

    /**
     * Called when the user starts a standard (non-history) quiz.
     */
    fun standardQuizLaunchPrepared(state: AppWorkflowState): AppWorkflowState {
        return state.copy(
            activeQuizLaunchSource = QuizLaunchSource.Standard,
        )
    }

    // ── Quiz → Filter return flow ────────────────────────────────────────

    /**
     * Called when the quiz exits and the user returns to the filter screen.
     */
    fun quizReturnedToFilter(state: AppWorkflowState): AppWorkflowState {
        val targetPane = when (state.activeQuizLaunchSource) {
            QuizLaunchSource.History -> RequestedFilterPane.History
            QuizLaunchSource.Standard -> RequestedFilterPane.Filters
        }
        return state.copy(
            pendingLaunchSource = null,
            activeQuizLaunchSource = QuizLaunchSource.Standard,
            requestedFilterPane = targetPane,
        )
    }

    // ── One-shot flag consumers ──────────────────────────────────────────

    /**
     * Consumes the [AppWorkflowState.requestedFilterPane] flag after the
     * filter screen has applied it.
     */
    fun filterPaneRequestConsumed(state: AppWorkflowState): AppWorkflowState {
        return state.copy(requestedFilterPane = null)
    }
}
