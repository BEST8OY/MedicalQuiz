package com.medicalquiz.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun HistorySelectionActions(
    hasSelection: Boolean,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    SettingsActionButton(
        onClick = onOpenSettings,
        icon = Icons.Filled.Settings,
    )
    if (hasSelection) {
        IconButton(onClick = onClearSelection) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
        }
        IconButton(onClick = onDeleteSelection) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected entries")
        }
    }
}
