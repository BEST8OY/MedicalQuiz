package com.medqb.app.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.components.EmptyStateMessage
import com.medqb.app.shared.ui.components.MedQBTopBar
import com.medqb.app.shared.ui.components.SettingsActionButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseSelectionScreen(
    databases: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefreshDatabases: () -> Unit,
    onDatabaseSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding)
                .consumeWindowInsets(PaddingValues(top = topPadding)),
            contentAlignment = Alignment.TopCenter,
        ) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefreshDatabases,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(PaddingValues(bottom = bottomPadding)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = bottomPadding + 24.dp
                    ),
                ) {
                    if (databases.isEmpty() && !isLoading) {
                        item(key = "empty_state") {
                            EmptyStateMessage(
                                title = if (errorMessage != null) "Couldn't load QBanks" else "No QBanks found",
                                subtitle = errorMessage
                                    ?: "Add .db files to /MedQB/QBanks and pull to refresh.",
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = databases,
                            key = { _, it -> it },
                        ) { index, dbName ->
                            DatabaseListItem(
                                name = dbName,
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = databases.size),
                                onClick = { onDatabaseSelected(dbName) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DatabaseListItem(
    name: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        content = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}
