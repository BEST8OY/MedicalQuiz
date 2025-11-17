package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    currentChecked: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (Set<Long>) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit
) {
    // Track selected ids locally
    val selected = remember { mutableStateOf(currentChecked.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        confirmButton = {
            Button(onClick = { onApply(selected.value.toSet()) }) { Text("Apply") }
        },
        dismissButton = {
            Button(onClick = onCancel) { Text("Cancel") }
        },
        text = {
            Column(modifier = Modifier.padding(4.dp)) {
                Button(onClick = { selected.value.clear(); onClear() }) { Text("Clear") }
                LazyColumn {
                    itemsIndexed(items) { _, item ->
                        val id = idProvider(item)
                        val label = labelProvider(item)
                        val checked = selected.value.contains(id)
                        androidx.compose.material3.ListItem(
                            headlineText = { Text(label) },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { checkedNow ->
                                        if (checkedNow) selected.value.add(id) else selected.value.remove(id)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    )
}
