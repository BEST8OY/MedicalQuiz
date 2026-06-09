package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.models.SubmissionMode
import com.medicalquiz.app.shared.ui.state.QuizUiState

class QuizSessionBoundaryUseCase(
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun restoreSessionForDatabase(databaseName: String): RestoreResult {
        val session = sessionRepository.restoreSessionAsync() ?: return RestoreResult.NoSession
        if (session.databaseName != databaseName) return RestoreResult.DatabaseMismatch

        return RestoreResult.Restored(
            sessionId = session.id,
            selectedSubjectIds = session.selectedSubjectIds.toSet(),
            selectedSystemIds = session.selectedSystemIds.toSet(),
            performanceFilter = session.performanceFilter,
            currentQuestionIndex = session.currentQuestionIndex,
            isLoggingEnabled = session.isLoggingEnabled,
            submissionMode = session.submissionMode,
        )
    }

    suspend fun saveSession(state: QuizUiState, appendToHistory: Boolean): String {
        if (state.databaseName.isBlank()) {
            sessionRepository.clearSessionAsync()
            return ""
        }

        return sessionRepository.saveSessionAsync(
            databaseName = state.databaseName,
            selectedSubjectIds = state.selectedSubjectIds,
            selectedSystemIds = state.selectedSystemIds,
            performanceFilter = state.performanceFilter,
            currentQuestionIndex = state.currentQuestionIndex,
            appendToHistory = appendToHistory,
            isLoggingEnabled = state.isLoggingEnabled,
            submissionMode = state.submissionMode,
        )
    }

    suspend fun clearSession() {
        sessionRepository.clearSessionAsync()
    }

    sealed interface RestoreResult {
        data object NoSession : RestoreResult
        data object DatabaseMismatch : RestoreResult
        data class Restored(
            val sessionId: String,
            val selectedSubjectIds: Set<Long>,
            val selectedSystemIds: Set<Long>,
            val performanceFilter: PerformanceFilter,
            val currentQuestionIndex: Int,
            val isLoggingEnabled: Boolean,
            val submissionMode: SubmissionMode,
        ) : RestoreResult
    }
}
