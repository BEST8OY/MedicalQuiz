package com.medicalquiz.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for MedicalQuiz application using Navigation 3.
 *
 * Routes are split into:
 * - persistent routes: restored on app restart
 * - transient routes: skipped when restoring navigation state (media/html overlays)
 */
@Serializable
sealed class MedicalQuizRoutes : NavKey {

    @Serializable
    data object DatabaseSelection : MedicalQuizRoutes()

    @Serializable
    data object Filter : MedicalQuizRoutes()

    @Serializable
    data class Quiz(
        val launchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    ) : MedicalQuizRoutes() {
        val launchedFromHistory: Boolean
            get() = launchSource == QuizLaunchSource.History
    }

    @Serializable
    data object Settings : MedicalQuizRoutes()

    @Serializable
    data class MediaViewer(
        val files: List<String>,
        val startIndex: Int = 0,
    ) : MedicalQuizRoutes()

    @Serializable
    data class HtmlViewer(
        val fileName: String,
    ) : MedicalQuizRoutes()

    val isTransient: Boolean
        get() = this is MediaViewer || this is HtmlViewer

    val isPersistent: Boolean
        get() = !isTransient

    companion object {
        fun sanitizeRestoredBackStack(stack: List<MedicalQuizRoutes>?): List<MedicalQuizRoutes>? {
            val persistentRoutes = stack
                ?.filter { it.isPersistent }
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            return persistentRoutes.takeIf { it.first() is DatabaseSelection }
        }
    }
}

@Serializable
enum class QuizLaunchSource {
    Standard,
    History,
}
