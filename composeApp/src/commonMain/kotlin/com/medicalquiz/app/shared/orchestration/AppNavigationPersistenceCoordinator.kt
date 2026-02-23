package com.medicalquiz.app.shared.orchestration

import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.navigation.NavigationStateRepository

class AppNavigationPersistenceCoordinator(
    private val navigationStateRepository: NavigationStateRepository,
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun onBackStackChanged(
        backStack: List<MedicalQuizRoutes>,
        selectedDatabase: String?,
    ) {
        navigationStateRepository.saveNavigationStateAsync(backStack, selectedDatabase)
        if (backStack.lastOrNull() is MedicalQuizRoutes.DatabaseSelection) {
            sessionRepository.refreshHistoryAsync()
        }
    }
}
