package com.medqb.app.shared.orchestration

import androidx.navigation3.runtime.NavKey
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.navigation.MedQBRoutes
import dev.zacsweers.metro.Inject

@Inject
class AppNavigationPersistenceCoordinator(
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun onBackStackChanged(backStack: List<NavKey>) {
        val lastRoute = backStack.lastOrNull() as? MedQBRoutes
        if (lastRoute is MedQBRoutes.DatabaseSelection || lastRoute is MedQBRoutes.Filter || lastRoute is MedQBRoutes.History) {
            sessionRepository.refreshHistoryAsync()
        }
    }
}
