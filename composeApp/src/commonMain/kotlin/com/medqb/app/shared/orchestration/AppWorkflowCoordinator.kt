package com.medqb.app.shared.orchestration

import com.medqb.app.shared.domain.RestoreSessionDecision
import com.medqb.app.shared.navigation.QuizLaunchSource

/**
 * Pure-logic coordinator that translates user actions and startup events into
 * [AppWorkflowState] mutations.
 *
 * The root `App` composable calls these methods and applies the returned state;
 * the coordinator never touches the back stack directly, keeping navigation
 * side-effects in the composable.
 */
class AppWorkflowCoordinator(
    private val startupCoordinator: AppStartupCoordinator,
) {

    // ── Bootstrap ────────────────────────────────────────────────────────

    /**
     * Builds the initial [AppWorkflowState] for a fresh start.
     * Saved state is restored by [rememberSaveable] in the composable layer.
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
    ): RestoreSessionDecision? {
        val dbName = state.selectedDatabase ?: return null
        return startupCoordinator.handleDatabaseSelection(
            selectedDatabase = dbName,
            initializedDatabase = state.initializedDatabase,
            pendingLaunchSource = state.pendingLaunchSource,
            shouldAttemptSessionRestore = state.shouldAttemptSessionRestore,
        )
    }

    /**
     * Applies a [RestoreSessionDecision] to the workflow state.
     */
    fun applyDatabaseSelectionDecision(
        state: AppWorkflowState,
        decision: RestoreSessionDecision,
    ): AppWorkflowState {
        return state.copy(
            initializedDatabase = decision.initializedDatabase,
            pendingLaunchSource = decision.pendingLaunchSource,
            shouldAttemptSessionRestore = decision.shouldAttemptSessionRestore,
        )
    }

    /**
     * Called when the user picks a database from the selection screen.
     */
    fun databaseSelected(state: AppWorkflowState, dbName: String): AppWorkflowState {
        return state.copy(
            selectedDatabase = dbName,
            initializedDatabase = null,
            pendingLaunchSource = null,
            activeQuizLaunchSource = QuizLaunchSource.Standard,
            shouldAttemptSessionRestore = false,
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
            shouldAttemptSessionRestore = false,
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

    /**
     * Consumes the [AppWorkflowState.shouldAttemptSessionRestore] flag
     * after the quiz entry has used it.
     */
    fun quizRestoreConsumed(state: AppWorkflowState): AppWorkflowState {
        return state.copy(shouldAttemptSessionRestore = false)
    }
}
