package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for MedQB application using Navigation 3.
 *
 * Routes are split into:
 * - persistent routes: restored on app restart
 * - transient routes: skipped when restoring navigation state (media/html overlays)
 */
@Serializable
sealed interface MedQBRoutes : NavKey {

    @Serializable
    data object DatabaseSelection : MedQBRoutes

    @Serializable
    data object Filter : MedQBRoutes

    @Serializable
    data object Quiz : MedQBRoutes

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

@Serializable
enum class QuizLaunchSource {
    Standard,
    History,
}

@Serializable
enum class FilterPane {
    Filters,
    History,
}

