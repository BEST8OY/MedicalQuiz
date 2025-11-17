package com.medicalquiz.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.data.database.PerformanceFilter

@Composable
fun PerformanceFilterDialog(current: PerformanceFilter, onSelect: (PerformanceFilter) -> Unit, onDismiss: () -> Unit) {
    val filters = PerformanceFilter.values()
    val selected = remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.padding(8.dp)) {
                filters.forEach { f ->
                    Row(Modifier.padding(4.dp)) {
                        RadioButton(
                            selected = (selected.value == f),
                            onClick = { selected.value = f }
                        )
                        Text(text = when (f) {
                            PerformanceFilter.ALL -> "All Questions"
                            PerformanceFilter.UNANSWERED -> "Not Attempted"
                            PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
                            PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
                            PerformanceFilter.EVER_CORRECT -> "Ever Correct"
                            PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
                        })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSelect(selected.value)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
