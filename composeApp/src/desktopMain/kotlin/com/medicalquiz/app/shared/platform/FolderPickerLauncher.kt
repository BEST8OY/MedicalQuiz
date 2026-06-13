package com.medicalquiz.app.shared.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFolderPickerLauncher(onResult: (String) -> Unit): () -> Unit {
    return {}
}
