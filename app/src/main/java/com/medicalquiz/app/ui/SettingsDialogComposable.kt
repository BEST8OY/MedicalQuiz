package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    initialLoggingEnabled: Boolean,
    onLoggingChanged: (Boolean) -> Unit,
    onResetLogs: () -> Unit
) {
    val enabled = remember { mutableStateOf(initialLoggingEnabled) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Logging")
        Switch(checked = enabled.value, onCheckedChange = { newValue ->
            enabled.value = newValue
            onLoggingChanged(newValue)
        })

        Button(onClick = onResetLogs, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = "Reset logs")
        }
    }
}
