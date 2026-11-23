package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import com.medqb.app.shared.ui.dialogs.components.BottomSheetHeader
import com.medqb.app.shared.ui.dialogs.components.FilterModalBottomSheet

/**
 * AMBOSS-style question status filter bottom sheet.
 * - Upper section: Multi-selective segmented checkboxes for question status (Not yet answered, Answered incorrectly, Answered correctly)
 * - Lower section: Mono-selective segmented radio buttons for attempt scope (Most recent attempt vs All attempts)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusFilterBottomSheet(
    current: StatusFilter,
    markedCount: Int? = null,
    onSelect: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStatuses by rememberSaveable(
        current,
        stateSaver = listSaver<Set<QuestionStatus>, String>(
            save = { it.map { status -> status.name } },
            restore = { names -> names.mapNotNull { runCatching { QuestionStatus.valueOf(it) }.getOrNull() }.toSet() }
        )
    ) { mutableStateOf(current.statuses) }
    var currentScope by rememberSaveable(current) { mutableStateOf(current.scope) }

    FilterModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        BottomSheetHeader(
            title = "Question Status",
            subtitle = "Include questions based on your history",
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Upper Section Header: Multi-selective statuses
            item {
                Text(
                    text = "INCLUDE QUESTIONS WITH FOLLOWING STATUS:",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            // Checkbox Items (Segmented group)
            itemsIndexed(
                items = QuestionStatus.entries,
                key = { _, status -> status.name }
            ) { index, status ->
                val isSelected = status in currentStatuses

                StatusCheckboxItem(
                    status = status,
                    isSelected = isSelected,
                    trailingBadge = if (status == QuestionStatus.MARKED) markedCount?.toString() else null,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = QuestionStatus.entries.size),
                    onToggle = {
                        currentStatuses = if (isSelected) {
                            currentStatuses - status
                        } else {
                            currentStatuses + status
                        }
                    }
                )
            }

            // Section Divider
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Lower Section Header: Mono-selective attempt scopes
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "HOW SHOULD STATUS FILTERS BE APPLIED?",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (currentStatuses.isNotEmpty() && !StatusFilter(currentStatuses, currentScope).usesAttemptScope) {
                        Text(
                            text = "Applies to answered correctly / incorrectly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Radio Button Items (Segmented group)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    StatusScope.entries.forEachIndexed { index, scope ->
                        val isSelected = scope == currentScope

                        ScopeRadioItem(
                            scope = scope,
                            isSelected = isSelected,
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = StatusScope.entries.size),
                            onSelect = { currentScope = scope }
                        )
                    }
                }
            }
        }

        // Actions Row
        val isDefault = currentStatuses == StatusFilter.DEFAULT.statuses &&
            currentScope == StatusFilter.DEFAULT.scope

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    currentStatuses = StatusFilter.DEFAULT.statuses
                    currentScope = StatusFilter.DEFAULT.scope
                },
                enabled = !isDefault,
                modifier = Modifier.testTag("status_reset_button")
            ) {
                Text("Reset")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("status_cancel_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSelect(StatusFilter(currentStatuses, currentScope)) },
                    modifier = Modifier.testTag("status_apply_button")
                ) {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCheckboxItem(
    status: QuestionStatus,
    isSelected: Boolean,
    shapes: ListItemShapes,
    onToggle: () -> Unit,
    trailingBadge: String? = null,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        checked = isSelected,
        onCheckedChange = { onToggle() },
        shapes = shapes,
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_checkbox_${status.name.lowercase()}"),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLeadingContentColor = MaterialTheme.colorScheme.primary,
        ),
        leadingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null
            )
        },
        trailingContent = trailingBadge?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Text(
                text = status.description,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        content = {
            Text(
                text = status.displayName,
                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeRadioItem(
    scope: StatusScope,
    isSelected: Boolean,
    shapes: ListItemShapes,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        selected = isSelected,
        onClick = onSelect,
        shapes = shapes,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
            }
            .testTag("status_scope_${scope.name.lowercase()}"),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLeadingContentColor = MaterialTheme.colorScheme.secondary,
        ),
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        },
        content = {
            Text(
                text = scope.displayName,
                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            )
        }
    )
}
