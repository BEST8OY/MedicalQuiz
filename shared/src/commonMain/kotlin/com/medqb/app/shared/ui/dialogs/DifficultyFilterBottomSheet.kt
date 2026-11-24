package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.ui.dialogs.components.BottomSheetActions
import com.medqb.app.shared.ui.dialogs.components.BottomSheetHeader
import com.medqb.app.shared.ui.dialogs.components.FilterModalBottomSheet
import com.medqb.app.shared.ui.state.enumSetSaver

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
    var currentSelection by rememberSaveable(
        selectedTiers,
        stateSaver = enumSetSaver<DifficultyTier>()
    ) {
        mutableStateOf(selectedTiers)
    }

    FilterModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        BottomSheetHeader(
            title = "Filter by difficulty",
            subtitle = "Select one or more difficulty tiers",
        )

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
                Text("Select all")
            }
            TextButton(
                onClick = { currentSelection = emptySet() },
                enabled = currentSelection.isNotEmpty(),
            ) {
                Text("Clear")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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

        BottomSheetActions(
            onCancel = onDismiss,
            onApply = {
                val normalized = if (currentSelection.size == tiers.size) emptySet() else currentSelection
                onApply(normalized)
            },
        )
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
        checked = isSelected,
        onCheckedChange = { onToggle() },
        shapes = shapes,
        modifier = Modifier.fillMaxWidth(),
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
