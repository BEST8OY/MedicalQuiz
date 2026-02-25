package com.medicalquiz.app.shared.ui.screens.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.ui.components.EmptyStateMessage
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun HistoryPane(
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTargetEntryIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
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
                        onClick = { onHistorySelected(entry) },
                        onSwipeDelete = {
                            deleteTargetEntryIds = setOf(entry.id)
                        },
                        onSwipeRename = {
                            renameTargetId = entry.id
                            renameText = entry.displayName()
                        },
                    )
                }
            }
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

@Composable
private fun HistoryItemCard(
    entry: QuizSessionRepository.QuizSession,
    onClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )
    val scope = rememberCoroutineScope()

    val cardShape = MaterialTheme.shapes.large

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        gesturesEnabled = true,
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
                .combinedClickable(onClick = onClick, onLongClick = onClick),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                            text = "QBank: ${entry.databaseName}",
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
