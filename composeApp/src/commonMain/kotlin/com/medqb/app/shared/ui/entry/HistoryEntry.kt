package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.screens.FilterPane
import com.medqb.app.shared.ui.screens.HistoryScreen
import com.medqb.app.shared.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun HistoryEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
) {
    val historyVM = viewModel<HistoryViewModel>(
        factory = viewModelFactory {
            initializer {
                graph.createHistoryViewModel()
            }
        }
    )
    val scope = rememberCoroutineScope()

    val databaseName by graph.activeDatabaseHolder.databaseName.collectAsStateWithLifecycle()
    val sessionHistory by historyVM.historyEntries.collectAsStateWithLifecycle()
    val scopedHistoryEntries = remember(sessionHistory, databaseName) {
        sessionHistory.filter { it.databaseName == databaseName }
    }

    val onHistorySelected = remember(historyVM, workflow, navigator, scope) {
        { entry: QuizSessionRepository.QuizSession ->
            scope.launch {
                val matchingDatabase = historyVM.restoreHistoryEntry(entry)
                if (matchingDatabase != null) {
                    workflow.onFilterSubjectsSync(
                        entry.selectedSubjectIds.toSet(),
                        entry.selectedSystemIds.toSet(),
                        entry.performanceFilter,
                    )
                    graph.filterStateHolder.setPendingHistoryEntryId(entry.id)
                    workflow.onHistoryLaunchPrepared(matchingDatabase)
                    navigator.navigateTo(MedQBRoutes.Quiz)
                }
            }
            Unit
        }
    }

    HistoryScreen(
        historyEntries = scopedHistoryEntries,
        onHistorySelected = onHistorySelected,
        onDeleteHistoryEntries = { historyVM.deleteHistoryEntries(it) },
        onRenameHistoryEntry = { id, name -> historyVM.renameHistoryEntry(id, name) },
        onCopyAllQids = { historyVM.getQuestionIdsForHistoryEntries(it) },
        selectedPane = FilterPane.History,
        onPaneSelected = { pane ->
            when (pane) {
                FilterPane.Filters -> navigator.switchTo(MedQBRoutes.Filter)
                FilterPane.History -> navigator.switchTo(MedQBRoutes.History)
            }
        },
    )
}
