package com.medicalquiz.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear

/**
 * Data class for selection items
 */
data class SelectionItem(
    val id: Long,
    val label: String
)

/**
 * A complete Jetpack Compose selection menu dialog with Material Design 3
 * @param title Dialog title
 * @param items List of items to select from
 * @param selectedIds Currently selected item IDs
 * @param onApply Callback when user confirms selection
 * @param onCancel Callback when user cancels
 * @param onClear Callback when user clears selection
 * @param showSelectAll Whether to show "Select All" button
 */
@Composable
fun SelectionMenuDialog(
    title: String,
    items: List<SelectionItem>,
    selectedIds: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    showSelectAll: Boolean = false
) {
    var selectedState by remember(selectedIds) { 
        mutableStateOf(selectedIds.toMutableSet())
    }
    val allIds = items.map { it.id }.toSet()
    val isAllSelected = selectedState.size == allIds.size && allIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Action buttons row with Material Design 3 styling
                if (showSelectAll) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = {
                                selectedState = if (isAllSelected) {
                                    mutableSetOf()
                                } else {
                                    allIds.toMutableSet()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isAllSelected) "Deselect All" else "Select All")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                }

                // Selection list with optimized scrolling
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items) { item ->
                        SelectionItemRow(
                            item = item,
                            isChecked = selectedState.contains(item.id),
                            onCheckedChange = { checked ->
                                selectedState = selectedState.toMutableSet().apply {
                                    if (checked) {
                                        add(item.id)
                                    } else {
                                        remove(item.id)
                                    }
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        selectedState = mutableSetOf()
                        onClear()
                    }) {
                        Text("Clear selection")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedState.toSet())
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Apply", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Individual selection item row with checkbox and Material Design 3 styling
 */
@Composable
private fun SelectionItemRow(
    item: SelectionItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        color = if (isChecked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
            )

            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Generic selection menu builder for any data type
 * @param title Dialog title
 * @param items List of items to display
 * @param selectedIds Currently selected IDs
 * @param labelProvider Function to extract label from item
 * @param idProvider Function to extract ID from item
 * @param onApply Callback when user confirms
 * @param onCancel Callback when user cancels
 * @param onClear Callback when user clears
 */
@Composable
fun <T> GenericSelectionMenuDialog(
    title: String,
    items: List<T>,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (Set<Long>) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    showSelectAll: Boolean = false
) {
    val selectionItems = items.map { item ->
        SelectionItem(
            id = idProvider(item),
            label = labelProvider(item)
        )
    }

    SelectionMenuDialog(
        title = title,
        items = selectionItems,
        selectedIds = selectedIds,
        onApply = onApply,
        onCancel = onCancel,
        onClear = onClear,
        showSelectAll = showSelectAll
    )
}
