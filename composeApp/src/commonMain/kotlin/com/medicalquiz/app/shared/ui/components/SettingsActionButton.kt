package com.medicalquiz.app.shared.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SettingsActionButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.Rounded.Settings,
    contentDescription: String = "Open settings",
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}
