package com.medqb.app.shared.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.ui.components.EmptyStateMessage
import com.medqb.app.shared.ui.richtext.setPlainText
import com.medqb.app.shared.ui.screens.media.PlatformBackHandler
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryPane(
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: (List<QuizSessionRepository.QuizSession>, (String) -> Unit) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onShowSnackbar: suspend (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    var selectedHistoryEntryIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var deleteTargetEntryIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var lastCopiedText by rememberSaveable { mutableStateOf("") }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val allHistoryEntryIds = remember(historyEntries) { historyEntries.map { it.id }.toSet() }

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
            onShowSnackbar("Copied QIDs to clipboard")
            lastCopiedText = ""
        }
    }

    DisposableEffect(Unit) {
        onDispose { onSelectionModeChanged(false) }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            contentPadding = PaddingValues(top = Spacing.Lg, bottom = ScreenLayout.BottomPaddingWithFab),
        ) {
            if (historyEntries.isEmpty()) {
                item {
                    EmptyStateMessage(
                        title = "No quiz history yet",
                        subtitle = "Completed or in-progress sessions will show here.",
                    )
                }
            } else {
                items(historyEntries, key = { it.id }) { entry ->
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
                            deleteTargetEntryIds = setOf(entry.id)
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
                val horizontalMargin = if (maxWidth >= ScreenLayout.WideWidthBreakpoint) Spacing.Xl else Spacing.Lg
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
                            deleteTargetEntryIds = selectedHistoryEntryIds
                            isFabMenuExpanded = false
                        },
                        text = { Text("Delete (${selectedHistoryEntryIds.size})") },
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    )
                }
            }
        }

        if (deleteTargetEntryIds.isNotEmpty()) {
            DeleteConfirmDialog(
                count = deleteTargetEntryIds.size,
                onConfirm = {
                    onDeleteHistoryEntries(deleteTargetEntryIds)
                    selectedHistoryEntryIds = selectedHistoryEntryIds - deleteTargetEntryIds
                    if (selectedHistoryEntryIds.isEmpty()) {
                        isFabMenuExpanded = false
                    }
                    deleteTargetEntryIds = emptySet()
                },
                onDismiss = { deleteTargetEntryIds = emptySet() },
            )
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

@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = if (count == 1) "entry" else "entries"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count $label?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename entry") },
        text = {
            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("Entry name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = currentName.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryItemCard(
    entry: QuizSessionRepository.QuizSession,
    isSelected: Boolean,
    selectionModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onSelectChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectionModeEnabled) {
        if (selectionModeEnabled && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth(),
        enableDismissFromStartToEnd = !selectionModeEnabled,
        enableDismissFromEndToStart = !selectionModeEnabled,
        gesturesEnabled = !selectionModeEnabled,
        onDismiss = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeRename()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            val isDeleteDirection = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val backgroundColor = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = Spacing.LgSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isDeleteDirection) Arrangement.End else Arrangement.Start,
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    val actionTint = if (isDeleteDirection) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    Icon(
                        imageVector = if (isDeleteDirection) Icons.Filled.Delete else Icons.Filled.Edit,
                        contentDescription = null,
                        tint = actionTint,
                        modifier = Modifier.size(ElementSize.IconMd),
                    )
                    Text(
                        text = if (isDeleteDirection) "Delete" else "Rename",
                        color = actionTint,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Spacing.Sm),
                    )
                }
            }
        },
    ) {
        val entryDisplayName = entry.displayName()

        ListItem(
            modifier = Modifier
                .semantics { role = Role.Button }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
            headlineContent = {
                Text(
                    text = entryDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xxs)) {
                    if (entryDisplayName != entry.databaseName) {
                        Text(
                            text = entry.databaseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                        )
                    }
                    Text(
                        text = "Q${entry.currentQuestionIndex + 1} \u00B7 ${relativeTimestamp(entry.updatedAtEpochMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            },
            leadingContent = {
                val checkboxWidth by animateDpAsState(
                    targetValue = if (selectionModeEnabled) ElementSize.IconLg else 0.dp,
                    label = "checkboxWidth",
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(checkboxWidth)) {
                        if (selectionModeEnabled) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onSelectChanged() },
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Quiz history entry",
                        tint = iconTint,
                        modifier = Modifier.size(ElementSize.IconLg),
                    )
                }
            },
        )
    }
}

private fun QuizSessionRepository.QuizSession.displayName(): String =
    entryName.ifBlank { databaseName }

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private fun relativeTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    val now = Clock.System.now()
    val then = Instant.fromEpochMilliseconds(epochMillis)
    val diffMs = now.minus(then).inWholeMilliseconds
    val diffMin = diffMs / 1000 / 60
    val diffHr = diffMin / 60
    val diffDay = diffHr / 24
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHr < 24 -> "${diffHr}h ago"
        diffDay < 7 -> "${diffDay}d ago"
        else -> {
            val ldt = then.toLocalDateTime(TimeZone.currentSystemDefault())
            val mon = ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$mon ${ldt.day}, ${ldt.hour}:${ldt.minute.toString().padStart(2, '0')}"
        }
    }
}
