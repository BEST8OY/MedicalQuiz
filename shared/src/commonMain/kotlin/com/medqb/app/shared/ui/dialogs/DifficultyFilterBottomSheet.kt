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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.DifficultyTier

/**
 * Material 3 multi-select bottom sheet for choosing question difficulty tiers using segmented list items.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DifficultyFilterBottomSheet(
    selectedTiers: Set<DifficultyTier>,
    counts: Map<DifficultyTier, Int>,
    onApply: (Set<DifficultyTier>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tiers = DifficultyTier.entries
    var currentSelection by remember(selectedTiers) {
        mutableStateOf(selectedTiers)
    }
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
                text = "Filter by difficulty",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select one or more difficulty tiers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { currentSelection = tiers.toSet() },
                enabled = currentSelection.size < tiers.size,
            ) {
                Text("Select All")
            }
            TextButton(
                onClick = { currentSelection = emptySet() },
                enabled = currentSelection.isNotEmpty(),
            ) {
                Text("Clear All")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f, fill = false),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(
                items = tiers,
                key = { _, tier -> tier.name }
            ) { index, tier ->
                val isSelected = tier in currentSelection
                val count = counts[tier]
                DifficultyFilterItem(
                    tier = tier,
                    isSelected = isSelected,
                    count = count,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = tiers.size),
                    onToggle = {
                        currentSelection = if (isSelected) {
                            currentSelection - tier
                        } else {
                            currentSelection + tier
                        }
                    },
                )
            }
        }

        // Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onApply(currentSelection) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DifficultyFilterItem(
    tier: DifficultyTier,
    isSelected: Boolean,
    count: Int?,
    shapes: ListItemShapes,
    onToggle: () -> Unit,
) {
    val countText = when {
        count == null -> tier.description
        count == 1 -> "${tier.description} • 1 question"
        else -> "${tier.description} • $count questions"
    }

    SegmentedListItem(
        selected = isSelected,
        onClick = onToggle,
        shapes = shapes,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
            },
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
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        content = {
            Text(
                text = tier.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
