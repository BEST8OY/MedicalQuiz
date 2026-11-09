package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope

/**
 * AMBOSS-style question status filter bottom sheet.
 * - Upper section: Multi-selective segmented checkboxes for question status (Not yet answered, Answered incorrectly, Answered correctly)
 * - Lower section: Mono-selective segmented radio buttons for attempt scope (Most recent attempt vs All attempts)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusFilterBottomSheet(
    current: StatusFilter,
    onSelect: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStatuses by remember { mutableStateOf(current.statuses) }
    var currentScope by remember { mutableStateOf(current.scope) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Question Status",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Include questions based on your history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f, fill = false),
            contentPadding = PaddingValues(vertical = 8.dp)
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
                Text(
                    text = "HOW SHOULD STATUS FILTERS BE APPLIED?",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Radio Button Items (Segmented group)
            itemsIndexed(
                items = StatusScope.entries,
                key = { _, scope -> scope.name }
            ) { index, scope ->
                val isSelected = scope == currentScope

                ScopeRadioItem(
                    scope = scope,
                    isSelected = isSelected,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = StatusScope.entries.size),
                    onSelect = { currentScope = scope }
                )
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
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        selected = isSelected,
        onClick = onToggle,
        shapes = shapes,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
            }
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
