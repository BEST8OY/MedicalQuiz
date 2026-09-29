package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing

/**
 * Material 3 multi-select dialog for choosing question difficulty tiers.
 */
@Composable
fun DifficultyFilterDialog(
    selectedTiers: Set<DifficultyTier>,
    counts: Map<DifficultyTier, Int>,
    onApply: (Set<DifficultyTier>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tiers = DifficultyTier.entries
    var currentSelection by remember(selectedTiers) {
        mutableStateOf(selectedTiers)
    }

    DialogShell(onDismiss = onDismiss) {
        DialogHeader(
            title = "Filter by difficulty",
            subtitle = "Select one or more difficulty tiers",
            onClose = onDismiss,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Large),
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
                .padding(horizontal = Inset.Large)
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            items(tiers) { tier ->
                val isSelected = tier in currentSelection
                val count = counts[tier]
                DifficultyFilterItem(
                    tier = tier,
                    isSelected = isSelected,
                    count = count,
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

        DialogActions(
            primaryText = "Apply",
            onPrimary = { onApply(currentSelection) },
            secondaryText = "Cancel",
            onSecondary = onDismiss,
        )
    }
}

@Composable
private fun DifficultyFilterItem(
    tier: DifficultyTier,
    isSelected: Boolean,
    count: Int?,
    onToggle: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Small, vertical = Spacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.Small, end = Spacing.MediumSmall),
            ) {
                Text(
                    text = tier.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                val countText = when {
                    count == null -> tier.description
                    count == 1 -> "${tier.description} • 1 question"
                    else -> "${tier.description} • $count questions"
                }

                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
