package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Inject
class HistoryViewModel(
    private val historyCoordinator: AppHistoryCoordinator,
    private val sessionRepository: QuizSessionRepository,
) : ViewModel() {

    val historyEntries: StateFlow<List<QuizSessionRepository.QuizSession>> =
        sessionRepository.historyEntries

    fun deleteHistoryEntries(entryIds: Set<String>) {
        viewModelScope.launch {
            historyCoordinator.deleteHistoryEntries(entryIds)
        }
    }

    fun renameHistoryEntry(entryId: String, newName: String) {
        viewModelScope.launch {
            historyCoordinator.renameHistoryEntry(entryId = entryId, newName = newName)
        }
    }

    suspend fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
    ): String? = historyCoordinator.restoreHistoryEntry(entry = entry)

    suspend fun getQuestionIdsForHistoryEntries(
        entries: List<QuizSessionRepository.QuizSession>,
    ): String = historyCoordinator.getQuestionIdsForHistoryEntries(entries)
}
