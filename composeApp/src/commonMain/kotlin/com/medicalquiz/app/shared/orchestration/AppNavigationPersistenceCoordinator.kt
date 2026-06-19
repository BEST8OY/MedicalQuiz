package com.medicalquiz.app.shared.orchestration

import androidx.navigation3.runtime.NavKey
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes

class AppNavigationPersistenceCoordinator(
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun onBackStackChanged(backStack: List<NavKey>) {
        val lastRoute = backStack.lastOrNull() as? MedicalQuizRoutes
        if (lastRoute is MedicalQuizRoutes.DatabaseSelection || lastRoute is MedicalQuizRoutes.Filter) {
            sessionRepository.refreshHistoryAsync()
        }
    }
}
