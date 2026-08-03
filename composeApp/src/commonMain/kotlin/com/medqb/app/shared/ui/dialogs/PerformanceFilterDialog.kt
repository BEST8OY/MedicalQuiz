package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell

/**
 * Dialog for selecting performance filter.
 */
@Composable
fun PerformanceFilterDialog(
    current: PerformanceFilter,
    onSelect: (PerformanceFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = PerformanceFilter.entries
    var selected by remember { mutableStateOf(current) }

    DialogShell(onDismiss = onDismiss) {
        Column {
            DialogHeader(
                title = "Filter by performance",
                subtitle = "Show questions based on your history",
                onClose = onDismiss
            )

            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = Inset.Large)
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                items(filters) { filter ->
                    PerformanceFilterItem(
                        filter = filter,
                        isSelected = selected == filter,
                        onSelected = { selected = filter }
                    )
                }
            }

            DialogActions(
                primaryText = "Apply",
                onPrimary = { onSelect(selected) },
                secondaryText = "Cancel",
                onSecondary = onDismiss
            )
        }
    }
}

@Composable
private fun PerformanceFilterItem(
    filter: PerformanceFilter,
    isSelected: Boolean,
    onSelected: () -> Unit
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
            .clickable(onClick = onSelected),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Small, vertical = Spacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelected,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.Small, end = Spacing.MediumSmall)
            ) {
                Text(
                    text = filter.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = filter.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun PerformanceFilter.displayName(): String = when (this) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}

private fun PerformanceFilter.description(): String = when (this) {
    PerformanceFilter.ALL -> "Include all questions regardless of history"
    PerformanceFilter.UNANSWERED -> "Questions you haven't answered yet"
    PerformanceFilter.LAST_CORRECT -> "Your most recent attempt was correct"
    PerformanceFilter.LAST_INCORRECT -> "Your most recent attempt was incorrect"
    PerformanceFilter.EVER_CORRECT -> "Answered correctly at least once"
    PerformanceFilter.EVER_INCORRECT -> "Answered incorrectly at least once"
}
