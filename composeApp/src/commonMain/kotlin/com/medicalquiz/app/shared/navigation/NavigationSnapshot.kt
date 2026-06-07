package com.medicalquiz.app.shared.navigation

/**
 * Snapshot of navigation state persisted to disk for process-death recovery.
 */
data class NavigationSnapshot(
    val routes: List<MedicalQuizRoutes>,
    val selectedDatabase: String?,
    val quizLaunchSource: QuizLaunchSource,
)
