package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StartFiltersPanel(
    subjectCount: Int,
    systemCount: Int,
    performanceLabel: String,
    previewCount: Int,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectPerformance: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Filters")
        Text(text = "$previewCount questions match")

        Button(onClick = onSelectSubjects, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Select Subjects ($subjectCount)")
        }

        Button(onClick = onSelectSystems, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Select Systems ($systemCount)")
        }

        Button(onClick = onSelectPerformance, modifier = Modifier.fillMaxWidth()) {
            Text(text = playcharForPerformance(performanceLabel))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
            Button(onClick = onStart) {
                Text(text = "Start")
            }
        }
    }
}

private fun playcharForPerformance(label: String): String = label
