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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.platform.rememberFolderPickerLauncher
import com.medicalquiz.app.shared.ui.components.MedicalQuizTopBar
import com.medicalquiz.app.shared.ui.components.SettingsActionButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseSelectionScreen(
    databases: List<String>,
    isLoading: Boolean,
    hasFolder: Boolean,
    onDatabaseSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onFolderPicked: () -> Unit,
    onResyncFolder: () -> Unit,
) {
    val launchFolderPicker = rememberFolderPickerLauncher { onFolderPicked() }

    Scaffold(
        topBar = {
            MedicalQuizTopBar(
                supportingText = "Select QBank",
                actions = {
                    if (hasFolder && databases.isNotEmpty()) {
                        IconButton(onClick = onResyncFolder) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Re-sync databases",
                            )
                        }
                    }
                    SettingsActionButton(
                        onClick = onOpenSettings,
                        icon = Icons.Filled.Settings,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    if (databases.isEmpty()) {
                        item {
                            EmptyStateWithFolderButton(
                                hasFolder = hasFolder,
                                onSelectFolder = launchFolderPicker,
                                onResync = onResyncFolder,
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
                }
            }
        }
    }
}

@Composable
private fun EmptyStateWithFolderButton(
    hasFolder: Boolean,
    onSelectFolder: () -> Unit,
    onResync: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasFolder) "No QBanks found in selected folder" else "No QBanks found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasFolder) "Add .db files to your QBanks folder and tap Re-sync, or select a different folder." else "Select a folder containing your quiz databases (.db files) and media.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSelectFolder) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(if (hasFolder) "Change Folder" else "Select Folder")
            }
            if (hasFolder) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onResync) {
                    Text("Re-sync")
                }
            }
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
