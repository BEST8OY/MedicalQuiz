package com.medicalquiz.app.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier
)
