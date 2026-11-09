package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell

/**
 * AMBOSS-style question status filter dialog.
 * - Upper section: Multi-selective checkboxes for question status (Not yet answered, Answered incorrectly, Answered correctly)
 * - Lower section: Mono-selective radio buttons for attempt scope (Most recent attempt vs All attempts)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusFilterDialog(
    current: StatusFilter,
    onSelect: (StatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStatuses by remember { mutableStateOf(current.statuses) }
    var currentScope by remember { mutableStateOf(current.scope) }

    DialogShell(onDismiss = onDismiss) {
        DialogHeader(
            title = "Question Status",
            subtitle = "Include questions based on your history",
            onClose = onDismiss
        )

        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Upper Section Header: Multi-selective statuses
            item {
                Text(
                    text = "INCLUDE QUESTIONS WITH FOLLOWING STATUS:",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Checkbox Items
            items(
                items = QuestionStatus.entries,
                key = { it.name }
            ) { status ->
                val isSelected = status in currentStatuses

                StatusCheckboxItem(
                    status = status,
                    isSelected = isSelected,
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
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Lower Section Header: Mono-selective attempt scopes
            item {
                Text(
                    text = "HOW SHOULD STATUS FILTERS BE APPLIED?",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Radio Button Items
            items(
                items = StatusScope.entries,
                key = { it.name }
            ) { scope ->
                val isSelected = scope == currentScope

                ScopeRadioItem(
                    scope = scope,
                    isSelected = isSelected,
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
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
                TextButton(
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .testTag("status_checkbox_${status.name.lowercase()}"),
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        ListItem(
            supportingContent = {
                Text(
                    text = status.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            Text(
                text = status.displayName,
                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeRadioItem(
    scope: StatusScope,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .testTag("status_scope_${scope.name.lowercase()}"),
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        ListItem(
            leadingContent = {
                RadioButton(
                    selected = isSelected,
                    onClick = null
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            Text(
                text = scope.displayName,
                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
