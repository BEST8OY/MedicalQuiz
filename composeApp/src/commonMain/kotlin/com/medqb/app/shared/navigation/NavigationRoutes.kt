package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for MedQB application using Navigation 3.
 *
 * All routes implement [NavKey] and are registered in SavedStateConfiguration
 * for state restoration across process recreation.
 */
@Serializable
sealed interface MedQBRoutes : NavKey {

    @Serializable
    data object DatabaseSelection : MedQBRoutes

    @Serializable
    data class Filter(
        val initialPaneName: String? = null,
    ) : MedQBRoutes

    @Serializable
    data class Quiz(
        val sessionId: String = "",
        val entryName: String = "",
        val initialQuestionIndex: Int = 0,
        val isLoggingEnabled: Boolean? = null,
        val submissionMode: String? = null,
    ) : MedQBRoutes

    @Serializable
    data object Settings : MedQBRoutes

    @Serializable
    data class MediaViewer(
        val files: List<String>,
        val startIndex: Int = 0,
    ) : MedQBRoutes

    @Serializable
    data class HtmlViewer(
        val fileName: String,
    ) : MedQBRoutes
}
