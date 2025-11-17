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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * A complete Jetpack Compose selection menu dialog
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
    val selectedState = remember { mutableStateOf(selectedIds.toMutableSet()) }
    val allIds = items.map { it.id }.toSet()
    val isAllSelected = remember(selectedState.value) {
        selectedState.value.size == allIds.size && allIds.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            selectedState.value.clear()
                            onClear()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }

                    if (showSelectAll) {
                        Button(
                            onClick = {
                                selectedState.value = if (isAllSelected) {
                                    mutableSetOf()
                                } else {
                                    allIds.toMutableSet()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(if (isAllSelected) "Deselect All" else "Select All")
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Selection list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(items) { item ->
                        SelectionItemRow(
                            item = item,
                            isChecked = selectedState.value.contains(item.id),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedState.value.add(item.id)
                                } else {
                                    selectedState.value.remove(item.id)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedState.value.toSet())
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f)
    )
}

/**
 * Individual selection item row with checkbox
 */
@Composable
private fun SelectionItemRow(
    item: SelectionItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp)
        )
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
