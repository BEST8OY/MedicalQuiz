package com.medqb.app.shared.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.components.EmptyStateMessage
import com.medqb.app.shared.ui.dialogs.RenameDialog
import com.medqb.app.shared.ui.richtext.setPlainText
import com.medqb.app.shared.ui.screens.media.PlatformBackHandler
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryPane(
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: suspend (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: (List<QuizSessionRepository.QuizSession>, (String) -> Unit) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onUndoDelete: suspend (QuizSessionRepository.QuizSession) -> Unit = {},
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    var selectedHistoryEntryIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var lastCopiedText by rememberSaveable { mutableStateOf("") }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val allHistoryEntryIds = remember(historyEntries) { historyEntries.map { it.id }.toSet() }
    val scope = rememberCoroutineScope()
    var restoredEntryVersions by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var pendingDeleteIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val visibleEntries = remember(historyEntries, pendingDeleteIds) {
        historyEntries.filter { it.id !in pendingDeleteIds }
    }

    fun deleteHistoryEntries(entriesToDelete: List<QuizSessionRepository.QuizSession>) {
        if (entriesToDelete.isEmpty()) return

        val deletedEntryIds = entriesToDelete.map { it.id }.toSet()
        pendingDeleteIds = pendingDeleteIds + deletedEntryIds
        selectedHistoryEntryIds = selectedHistoryEntryIds - deletedEntryIds
        if (selectedHistoryEntryIds.isEmpty()) {
            isFabMenuExpanded = false
        }

        scope.launch {
            onDeleteHistoryEntries(deletedEntryIds)
            onShowSnackbar(
                SnackbarMessage.Action(
                    message = if (deletedEntryIds.size == 1) {
                        "Entry deleted"
                    } else {
                        "${deletedEntryIds.size} entries deleted"
                    },
                    actionLabel = "Undo",
                    onActionPerformed = {
                        entriesToDelete.forEach { entry ->
                            onUndoDelete(entry)
                        }
                        restoredEntryVersions = restoredEntryVersions.withIncrementedVersions(deletedEntryIds)
                        pendingDeleteIds = pendingDeleteIds - deletedEntryIds
                    },
                )
            )
            pendingDeleteIds = pendingDeleteIds - deletedEntryIds
        }
    }

    PlatformBackHandler(
        enabled = selectedHistoryEntryIds.isNotEmpty() || isFabMenuExpanded,
        onBack = {
            if (isFabMenuExpanded) {
                isFabMenuExpanded = false
            } else {
                selectedHistoryEntryIds = emptySet()
            }
        },
    )

    LaunchedEffect(allHistoryEntryIds) {
        selectedHistoryEntryIds = selectedHistoryEntryIds.intersect(allHistoryEntryIds)
        if (selectedHistoryEntryIds.isEmpty()) {
            isFabMenuExpanded = false
        }
    }

    LaunchedEffect(selectedHistoryEntryIds) {
        onSelectionModeChanged(selectedHistoryEntryIds.isNotEmpty())
    }

    LaunchedEffect(lastCopiedText) {
        if (lastCopiedText.isNotBlank()) {
            clipboard.setPlainText(AnnotatedString(lastCopiedText))
            onShowSnackbar(SnackbarMessage.Simple("Copied QIDs to clipboard"))
            lastCopiedText = ""
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onDismissSnackbar()
            onSelectionModeChanged(false)
            pendingDeleteIds = emptySet()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = ScreenLayout.WideWidthBreakpoint)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                contentPadding = PaddingValues(top = Spacing.Large, bottom = ScreenLayout.BottomPaddingWithFab),
            ) {
                if (visibleEntries.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            title = "No quiz history yet",
                            subtitle = "Completed or in-progress sessions will show here.",
                        )
                    }
                } else {
                    items(
                        items = visibleEntries,
                        key = { entry -> "${entry.id}:${restoredEntryVersions[entry.id] ?: 0}" },
                    ) { entry ->
                        HistoryItemCard(
                            entry = entry,
                            isSelected = entry.id in selectedHistoryEntryIds,
                            selectionModeEnabled = selectedHistoryEntryIds.isNotEmpty(),
                            onClick = {
                                if (selectedHistoryEntryIds.isNotEmpty()) {
                                    selectedHistoryEntryIds = selectedHistoryEntryIds.toggle(entry.id)
                                } else {
                                    onHistorySelected(entry)
                                }
                            },
                            onLongPress = {
                                selectedHistoryEntryIds = selectedHistoryEntryIds.toggle(entry.id)
                            },
                            onSwipeDelete = {
                                deleteHistoryEntries(listOf(entry))
                            },
                            onSwipeRename = {
                                renameTargetId = entry.id
                                renameText = entry.displayName()
                            },
                            onSelectChanged = {
                                selectedHistoryEntryIds = selectedHistoryEntryIds.toggle(entry.id)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (selectedHistoryEntryIds.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val horizontalMargin = if (maxWidth >= ScreenLayout.WideWidthBreakpoint) Spacing.ExtraLarge else Spacing.Large
                    FloatingActionButtonMenu(
                        expanded = isFabMenuExpanded,
                        button = {
                            ToggleFloatingActionButton(
                                checked = isFabMenuExpanded,
                                onCheckedChange = { isFabMenuExpanded = it },
                            ) {
                                Icon(
                                    imageVector = if (isFabMenuExpanded) Icons.Filled.Close else Icons.Filled.MoreVert,
                                    contentDescription = if (isFabMenuExpanded) "Close actions" else "More actions",
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = horizontalMargin, bottom = ScreenLayout.FabBottomPadding)
                            .navigationBarsPadding(),
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = {
                                selectedHistoryEntryIds = allHistoryEntryIds
                                isFabMenuExpanded = false
                            },
                            text = { Text("Select all") },
                            icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        )

                        FloatingActionButtonMenuItem(
                            onClick = {
                                val selectedEntries = historyEntries
                                    .filter { it.id in selectedHistoryEntryIds }
                                onCopyAllQids(selectedEntries) { qidsText ->
                                    lastCopiedText = qidsText
                                }
                                isFabMenuExpanded = false
                            },
                            text = { Text("Copy QIDs (${selectedHistoryEntryIds.size})") },
                            icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        )

                        FloatingActionButtonMenuItem(
                            onClick = {
                                val entriesToDelete = historyEntries
                                    .filter { it.id in selectedHistoryEntryIds }
                                deleteHistoryEntries(entriesToDelete)
                            },
                            text = { Text("Delete (${selectedHistoryEntryIds.size})") },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        )
                    }
                }
            }

            if (renameTargetId != null) {
                RenameDialog(
                    currentName = renameText,
                    onNameChange = { renameText = it },
                    onConfirm = {
                        val targetId = renameTargetId ?: return@RenameDialog
                        onRenameHistoryEntry(targetId, renameText)
                        renameTargetId = null
                        renameText = ""
                    },
                    onDismiss = {
                        renameTargetId = null
                        renameText = ""
                    },
                )
            }
        }
    }
}
