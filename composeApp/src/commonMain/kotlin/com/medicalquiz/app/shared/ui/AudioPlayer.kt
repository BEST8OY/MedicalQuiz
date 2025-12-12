package com.medicalquiz.app.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AudioPlayer(
    filePath: String,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true
)
