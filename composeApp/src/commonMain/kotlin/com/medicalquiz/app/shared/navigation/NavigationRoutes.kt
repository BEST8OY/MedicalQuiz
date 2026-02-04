package com.medicalquiz.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for MedicalQuiz application using Navigation 3.
 *
 * All routes implement [NavKey] for type-safe navigation with automatic serialization
 * using kotlinx.serialization.
 *
 * Note: Dialog overlays (settings, filters, jump-to, error) are handled within
 * QuizRoot using local state and are not part of the navigation back stack.
 */
@Serializable
sealed class MedicalQuizRoutes : NavKey {

    /**
     * Database selection screen - app entry point.
     * User selects a .db file to load quiz data from.
     */
    @Serializable
    data object DatabaseSelection : MedicalQuizRoutes()

    /**
     * Filter screen - pre-quiz configuration.
     * User selects subjects, systems, and performance filters.
     */
    @Serializable
    data object Filter : MedicalQuizRoutes()

    /**
     * Main quiz screen - question display and answering.
     * Contains the navigation drawer for in-quiz navigation.
     */
    @Serializable
    data object Quiz : MedicalQuizRoutes()

    /**
     * Media viewer screen - full-screen media display.
     * Supports images, videos, and audio files with swipe navigation.
     *
     * @property files List of media file names to display
     * @property startIndex Initial media index to show (0-based)
     */
    @Serializable
    data class MediaViewer(
        val files: List<String>,
        val startIndex: Int = 0,
    ) : MedicalQuizRoutes()

/**
 * HTML viewer screen - displays HTML content files.
 *
 * @property fileName Name of the HTML file to display
 */
@Serializable
data class HtmlViewer(
    val fileName: String,
) : MedicalQuizRoutes()

/**
 * Subject selection screen - full-screen subject filter.
 */
@Serializable
data object SubjectSelection : MedicalQuizRoutes()

/**
 * System selection screen - full-screen system filter.
 */
@Serializable
data object SystemSelection : MedicalQuizRoutes()

/**
 * Performance selection screen - full-screen performance filter.
 */
@Serializable
data object PerformanceSelection : MedicalQuizRoutes()
}
