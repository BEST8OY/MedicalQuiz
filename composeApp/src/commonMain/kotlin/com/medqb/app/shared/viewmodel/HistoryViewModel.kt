package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Inject
class HistoryViewModel(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val historyCoordinator: AppHistoryCoordinator,
    private val sessionRepository: QuizSessionRepository,
) : ViewModel() {

    val historyEntries: StateFlow<List<QuizSessionRepository.QuizSession>> =
        sessionRepository.historyEntries

    fun refresh() {
        viewModelScope.launch {
            sessionRepository.refreshHistoryAsync()
        }
    }

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
    ): String = withContext(Dispatchers.IO) {
        val db = activeDatabaseHolder.databaseProvider.value
        buildString {
            entries.forEach { entry ->
                val questionIds = db?.getQuestionIds(
                    subjectIds = entry.selectedSubjectIds,
                    systemIds = entry.selectedSystemIds,
                    performanceFilter = entry.performanceFilter,
                ) ?: emptyList()
                questionIds.forEach { qid ->
                    appendLine(qid)
                }
            }
        }
    }
}
