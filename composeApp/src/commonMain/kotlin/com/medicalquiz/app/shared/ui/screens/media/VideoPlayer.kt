package com.medicalquiz.app.shared.ui.screens.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true
)
