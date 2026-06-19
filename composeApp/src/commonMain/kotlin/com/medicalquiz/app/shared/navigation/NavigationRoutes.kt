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
sealed interface MedicalQuizRoutes : NavKey {

    @Serializable
    data object DatabaseSelection : MedicalQuizRoutes

    @Serializable
    data object Filter : MedicalQuizRoutes

    @Serializable
    data object Quiz : MedicalQuizRoutes

    @Serializable
    data object Settings : MedicalQuizRoutes

    @Serializable
    data class MediaViewer(
        val files: List<String>,
        val startIndex: Int = 0,
    ) : MedicalQuizRoutes

    @Serializable
    data class HtmlViewer(
        val fileName: String,
    ) : MedicalQuizRoutes
}

@Serializable
enum class QuizLaunchSource {
    Standard,
    History,
}
