package com.medicalquiz.app.shared.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFolderPickerLauncher(onResult: (String) -> Unit): () -> Unit
