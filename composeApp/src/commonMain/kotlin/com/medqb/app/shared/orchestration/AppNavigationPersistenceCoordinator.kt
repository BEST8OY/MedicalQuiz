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
        // History data is now owned by HistoryViewModel — no async refresh needed here.
    }
}
