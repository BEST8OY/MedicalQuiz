package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Coordinates app-level database selection lifecycle and associated filter-state resets.
 *
 * The root `App` composable calls these methods via [rememberAppWorkflow];
 * navigation side-effects remain in the composable layer.
 */
@Inject
@SingleIn(AppScope::class)
class AppWorkflowCoordinator(
    private val startupCoordinator: AppStartupCoordinator,
    private val filterStateHolder: FilterStateHolder,
) {
    /**
     * Delegates to [AppStartupCoordinator] for serialized DB initialization.
     */
    suspend fun handleDatabaseSelection(selectedDatabase: String?): DatabaseSelectionDecision? {
        val dbName = selectedDatabase ?: return null
        return startupCoordinator.handleDatabaseSelection(selectedDatabase = dbName)
    }

    /**
     * Closes the active database connection, if any.
     */
    suspend fun closeActiveDatabase() {
        startupCoordinator.closeActiveDatabase()
    }

    /**
     * Called when the user picks a database from the selection screen.
     */
    fun onDatabaseSelected(dbName: String): String {
        filterStateHolder.reset()
        return dbName
    }

    /**
     * Called when the user returns to database selection.
     */
    fun onDatabaseSelectionRequested(): String? = null

    /**
     * Called when a history entry has been restored and the quiz screen
     * should open with the matching database.
     */
    fun onHistoryLaunchPrepared(matchingDatabase: String): String = matchingDatabase
}
