package com.medqb.app.shared.orchestration

import com.medqb.app.shared.navigation.QuizLaunchSource

/**
 * Immutable state bucket for the app-level workflow flags that were previously
 * scattered across individual `rememberSaveable` variables in `App.kt`.
 *
 * This lets the root composable hold a single `var workflowState` instead of
 * five separate vars, making state transitions explicit and easier to test.
 */
data class AppWorkflowState(
    /** Database the user most recently selected (may not yet be initialised). */
    val selectedDatabase: String? = null,
    /** Database that has been fully initialised (DB connection established). */
    val initializedDatabase: String? = null,
    /** Non-null while a history or other deferred quiz launch is pending. */
    val pendingLaunchSource: QuizLaunchSource? = null,
    /** The launch source the active quiz session is running under. */
    val activeQuizLaunchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    /** Which filter pane to reveal when returning from a quiz. */
    val requestedFilterPane: RequestedFilterPane? = null,
    /** Whether the quiz entry should attempt to restore a prior session. */
    val shouldAttemptSessionRestore: Boolean = false,
)

/**
 * Mirror of [com.medqb.app.shared.ui.screens.FilterPane] used at
 * the orchestration layer so the domain/orchestration packages stay
 * decoupled from UI enums.
 */
enum class RequestedFilterPane {
    Filters,
    History,
}
