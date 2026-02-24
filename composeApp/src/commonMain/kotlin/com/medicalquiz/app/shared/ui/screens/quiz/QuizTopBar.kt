package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.medicalquiz.app.shared.ui.components.MedicalQuizTopBar
import com.medicalquiz.app.shared.ui.components.SettingsActionButton

@Composable
internal fun TopBar(
    title: String,
    onResetLogClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    MedicalQuizTopBar(
        headline = title.ifBlank { "Medical Quiz" },
        actions = {
            IconButton(onClick = onResetLogClick) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset current question log"
                )
            }
            SettingsActionButton(
                onClick = onSettingsClick,
                contentDescription = "Settings",
            )
        },
    )
}
