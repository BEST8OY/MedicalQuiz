package com.medqb.app.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.components.EmptyStateMessage
import com.medqb.app.shared.ui.components.MedQBTopBar
import com.medqb.app.shared.ui.components.SettingsActionButton
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSelectionScreen(
    databases: List<String>,
    isLoading: Boolean,
    onRefreshDatabases: () -> Unit,
    onDatabaseSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            MedQBTopBar(
                supportingText = "Select QBank",
                actions = {
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
            contentAlignment = Alignment.TopCenter,
        ) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefreshDatabases,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = ScreenLayout.WideWidthBreakpoint),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
                    contentPadding = PaddingValues(start = Inset.Medium, top = Inset.Medium, end = Inset.Medium, bottom = Spacing.Large),
                ) {
                    if (databases.isEmpty() && !isLoading) {
                        item {
                            EmptyStateMessage(
                                title = "No QBanks found",
                                subtitle = "Add .db files to /MedQB/QBanks and pull to refresh.",
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
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.ExtraLarge),
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
