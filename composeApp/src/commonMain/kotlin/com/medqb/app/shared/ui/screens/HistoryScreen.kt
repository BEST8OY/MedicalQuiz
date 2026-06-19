package com.medqb.app.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.ui.screens.history.HistoryPane

@Composable
internal fun HistoryScreen(
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: suspend (List<QuizSessionRepository.QuizSession>) -> String,
    selectedPane: FilterPane,
    onPaneSelected: (FilterPane) -> Unit,
) {
    var historySelectionMode by rememberSaveable { mutableStateOf(false) }

    FilterPaneScaffold(
        selectedPane = selectedPane,
        onPaneSelected = onPaneSelected,
        showPaneToolbar = !historySelectionMode,
    ) {
        HistoryPane(
            historyEntries = historyEntries,
            onHistorySelected = onHistorySelected,
            onDeleteHistoryEntries = onDeleteHistoryEntries,
            onRenameHistoryEntry = onRenameHistoryEntry,
            onCopyAllQids = onCopyAllQids,
            onSelectionModeChanged = { historySelectionMode = it },
        )
    }
}
