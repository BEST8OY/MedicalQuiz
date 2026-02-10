package com.medicalquiz.app.shared.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.toShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.platform.FileSystemHelper
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private enum class SelectionPane {
    Database,
    History,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSelectionScreen(
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onDatabaseSelected: (String) -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
) {
    var databases by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var selectedPane by rememberSaveable { mutableStateOf(SelectionPane.Database) }

    fun loadDatabases() {
        isLoading = true
        databases = FileSystemHelper.listDatabases()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadDatabases()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Medical Quiz")
                        Text(
                            text = if (selectedPane == SelectionPane.Database) "Select database" else "Recent sessions",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    if (selectedPane == SelectionPane.Database) {
                        IconButton(onClick = { loadDatabases() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh databases")
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
                                onClick = { onHistorySelected(entry) },
                                onDelete = { onDeleteHistoryEntry(entry.id) },
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
        ToolbarModeButton(
            text = "Databases",
            icon = {
                Icon(Icons.Filled.Storage, contentDescription = null)
            },
            selected = selectedPane == SelectionPane.Database,
            onClick = { onPaneSelected(SelectionPane.Database) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        ToolbarModeButton(
            text = "History",
            icon = {
                Icon(Icons.Filled.History, contentDescription = null)
            },
            selected = selectedPane == SelectionPane.History,
            onClick = { onPaneSelected(SelectionPane.History) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarModeButton(
    text: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            shape = MaterialShapes.ClamShell.toShape()
        ) {
            icon()
            Text(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = MaterialShapes.Bun.toShape()
        ) {
            icon()
            Text(text = text)
        }
    }
}

@Composable
private fun HistoryItemCard(
    entry: QuizSessionRepository.QuizSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.databaseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Question ${entry.currentQuestionIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTimestamp(entry.updatedAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onDelete,
                label = { Text("Delete") },
                leadingIcon = {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun DatabaseItemCard(
    name: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown time"
    val localDateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(
        localDateTime.year,
        localDateTime.monthNumber,
        localDateTime.dayOfMonth,
        localDateTime.hour,
        localDateTime.minute,
    )
}
