package com.medicalquiz.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.QuizSessionRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private enum class SelectionPane {
    Database,
    History,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSelectionScreen(
    databases: List<String>,
    isLoading: Boolean,
    onRefreshDatabases: () -> Unit,
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onDatabaseSelected: (String) -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
) {
    var selectedPane by rememberSaveable { mutableStateOf(SelectionPane.Database) }
    var selectedHistoryEntryIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var deleteTargetEntryIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Medical Quiz",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = when {
                                selectedPane == SelectionPane.Database -> "Select database"
                                selectedHistoryEntryIds.isNotEmpty() -> "${selectedHistoryEntryIds.size} selected"
                                else -> "Recent sessions"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                actions = {
                    when (selectedPane) {
                        SelectionPane.Database -> {
                            IconButton(onClick = onRefreshDatabases) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh databases")
                            }
                        }
                        SelectionPane.History -> {
                            if (selectedHistoryEntryIds.isNotEmpty()) {
                                IconButton(onClick = { selectedHistoryEntryIds = emptySet() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                                }
                                IconButton(onClick = { deleteTargetEntryIds = selectedHistoryEntryIds }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete selected entries")
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            ) {
                if (selectedPane == SelectionPane.Database) {
                    if (databases.isEmpty() && !isLoading) {
                        item {
                            EmptyState(
                                title = "No databases found",
                                subtitle = "Add .db files to the app directory and refresh.",
                            )
                        }
                    } else {
                        items(databases) { dbName ->
                            DatabaseItemCard(
                                name = dbName,
                                onClick = { onDatabaseSelected(dbName) },
                            )
                        }
                    }
                } else {
                    if (historyEntries.isEmpty()) {
                        item {
                            EmptyState(
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
                                swipingEnabled = selectedHistoryEntryIds.isEmpty(),
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
                            )
                        }
                    }
                }
            }

            FloatingToolbar(
                selectedPane = selectedPane,
                onPaneSelected = { selectedPane = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
            )
        }

        if (deleteTargetEntryIds.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { deleteTargetEntryIds = emptySet() },
                title = { Text("Delete ${deleteTargetEntryIds.size} entr${if (deleteTargetEntryIds.size == 1) "y" else "ies"}?") },
                text = { Text("This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteHistoryEntries(deleteTargetEntryIds)
                            selectedHistoryEntryIds = selectedHistoryEntryIds - deleteTargetEntryIds
                            deleteTargetEntryIds = emptySet()
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargetEntryIds = emptySet() }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (renameTargetId != null) {
            AlertDialog(
                onDismissRequest = {
                    renameTargetId = null
                    renameText = ""
                },
                title = { Text("Rename entry") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Entry name") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetId = renameTargetId ?: return@TextButton
                            onRenameHistoryEntry(targetId, renameText)
                            renameTargetId = null
                            renameText = ""
                        },
                        enabled = renameText.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            renameTargetId = null
                            renameText = ""
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingToolbar(
    selectedPane: SelectionPane,
    onPaneSelected: (SelectionPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Database tab - ToggleButton: icon + text when checked, text only when unchecked
        ToggleButton(
            checked = selectedPane == SelectionPane.Database,
            onCheckedChange = { if (it) onPaneSelected(SelectionPane.Database) },
            modifier = Modifier.padding(horizontal = 4.dp),
            shapes = ToggleButtonDefaults.shapes(
                shape = ToggleButtonDefaults.squareShape,
                pressedShape = ToggleButtonDefaults.roundShape,
                checkedShape = ToggleButtonDefaults.roundShape
            ),
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            // Show icon only when checked
            if (selectedPane == SelectionPane.Database) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Databases")
        }

        // History tab - ToggleButton: icon + text when checked, text only when unchecked
        ToggleButton(
            checked = selectedPane == SelectionPane.History,
            onCheckedChange = { if (it) onPaneSelected(SelectionPane.History) },
            modifier = Modifier.padding(horizontal = 4.dp),
            shapes = ToggleButtonDefaults.shapes(
                shape = ToggleButtonDefaults.squareShape,
                pressedShape = ToggleButtonDefaults.roundShape,
                checkedShape = ToggleButtonDefaults.roundShape
            ),
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            // Show icon only when checked
            if (selectedPane == SelectionPane.History) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("History")
        }
    }
}

@Composable
private fun HistoryItemCard(
    entry: QuizSessionRepository.QuizSession,
    isSelected: Boolean,
    selectionModeEnabled: Boolean,
    swipingEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onSelectChanged: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(swipingEnabled) {
        if (!swipingEnabled && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    val cardShape = MaterialTheme.shapes.large

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape),
        enableDismissFromStartToEnd = swipingEnabled,
        enableDismissFromEndToStart = swipingEnabled,
        gesturesEnabled = swipingEnabled,
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
                    .clip(cardShape)
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
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
                    )
                }
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectionModeEnabled) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectChanged() },
                    )
                }
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                val entryDisplayName = entry.displayName()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entryDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (entryDisplayName != entry.databaseName) {
                        Text(
                            text = "Database: ${entry.databaseName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Question ${entry.currentQuestionIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatTimestamp(entry.updatedAtEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@Composable
private fun DatabaseItemCard(
    name: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun QuizSessionRepository.QuizSession.displayName(): String =
    entryName.ifBlank { databaseName }

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown time"
    val localDateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val month = localDateTime.month.ordinal + 1
    return "%04d-%02d-%02d %02d:%02d".format(
        localDateTime.year,
        month,
        localDateTime.day,
        localDateTime.hour,
        localDateTime.minute,
    )
}
