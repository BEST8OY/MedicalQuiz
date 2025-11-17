package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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
    onClear: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
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

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Clear")
        }
    }
    }
}

private fun playcharForPerformance(label: String): String = label
