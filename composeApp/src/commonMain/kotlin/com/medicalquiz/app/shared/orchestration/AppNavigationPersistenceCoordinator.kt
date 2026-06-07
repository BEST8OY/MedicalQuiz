package com.medicalquiz.app.shared.orchestration

import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.navigation.NavigationStateRepository
import com.medicalquiz.app.shared.navigation.QuizLaunchSource

class AppNavigationPersistenceCoordinator(
    private val navigationStateRepository: NavigationStateRepository,
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun onBackStackChanged(
        backStack: List<MedicalQuizRoutes>,
        selectedDatabase: String?,
        quizLaunchSource: QuizLaunchSource = QuizLaunchSource.Standard,
    ) {
        navigationStateRepository.saveNavigationStateAsync(backStack, selectedDatabase, quizLaunchSource)
        if (
            backStack.lastOrNull() is MedicalQuizRoutes.DatabaseSelection ||
            backStack.lastOrNull() is MedicalQuizRoutes.Filter
        ) {
            sessionRepository.refreshHistoryAsync()
        }
    }
}
