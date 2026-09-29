package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.UserDataManager
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
        userDataManager: UserDataManager,
    ): DatabaseSelectionDecision? {
        val dbName = state.selectedDatabase ?: return null
        return startupCoordinator.handleDatabaseSelection(
            selectedDatabase = dbName,
            userDataManager = userDataManager,
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
        )
    }

    /**
     * Called when the user explicitly navigates back to database selection.
     */
    fun databaseSelectionRequested(state: AppWorkflowState): AppWorkflowState {
        return state.copy(
            selectedDatabase = null,
            initializedDatabase = null,
        )
    }

    // ── Quiz launch flow ─────────────────────────────────────────────────

    /**
     * Called when a history entry has been restored and the quiz screen
     * should open with the matching database.
     */
    fun historyLaunchPrepared(
        state: AppWorkflowState,
        matchingDatabase: String,
    ): AppWorkflowState {
        return state.copy(
            selectedDatabase = matchingDatabase,
        )
    }
}
