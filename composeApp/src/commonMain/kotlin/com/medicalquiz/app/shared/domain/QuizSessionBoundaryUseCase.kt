package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.ui.state.QuizUiState

class QuizSessionBoundaryUseCase(
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun restoreSessionForDatabase(databaseName: String): RestoreResult {
        val session = sessionRepository.restoreSessionAsync() ?: return RestoreResult.NoSession
        if (session.databaseName != databaseName) return RestoreResult.DatabaseMismatch

        return RestoreResult.Restored(
            selectedSubjectIds = session.selectedSubjectIds.toSet(),
            selectedSystemIds = session.selectedSystemIds.toSet(),
            performanceFilter = session.performanceFilter,
            currentQuestionIndex = session.currentQuestionIndex,
        )
    }

    suspend fun saveSession(state: QuizUiState, appendToHistory: Boolean) {
        if (state.databaseName.isBlank()) {
            sessionRepository.clearSessionAsync()
            return
        }

        sessionRepository.saveSessionAsync(
            databaseName = state.databaseName,
            selectedSubjectIds = state.selectedSubjectIds,
            selectedSystemIds = state.selectedSystemIds,
            performanceFilter = state.performanceFilter,
            currentQuestionIndex = state.currentQuestionIndex,
            appendToHistory = appendToHistory,
        )
    }

    suspend fun clearSession() {
        sessionRepository.clearSessionAsync()
    }

    sealed interface RestoreResult {
        data object NoSession : RestoreResult
        data object DatabaseMismatch : RestoreResult
        data class Restored(
            val selectedSubjectIds: Set<Long>,
            val selectedSystemIds: Set<Long>,
            val performanceFilter: PerformanceFilter,
            val currentQuestionIndex: Int,
        ) : RestoreResult
    }
}
