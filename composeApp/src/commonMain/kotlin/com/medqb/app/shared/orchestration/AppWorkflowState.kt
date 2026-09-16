package com.medqb.app.shared.orchestration

/**
 * Immutable state bucket for app-level workflow state.
 */
data class AppWorkflowState(
    /** Database the user most recently selected (may not yet be initialised). */
    val selectedDatabase: String? = null,
    /** Database that has been fully initialised (DB connection established). */
    val initializedDatabase: String? = null,
)
