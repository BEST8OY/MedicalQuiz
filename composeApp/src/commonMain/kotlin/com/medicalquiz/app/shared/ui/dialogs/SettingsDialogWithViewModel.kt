package com.medicalquiz.app.shared.ui.dialogs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Settings dialog connected to QuizViewModel.
 */
@Composable
fun SettingsDialogWithViewModel(
    isVisible: Boolean,
    viewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onResetLogsRequested: () -> Unit
) {
    if (!isVisible) return

    val loggingEnabled = viewModel.settingsRepository?.isLoggingEnabled
        ?.collectAsStateWithLifecycle(false)?.value ?: false
    val showMetadata = viewModel.settingsRepository?.showMetadata
        ?.collectAsStateWithLifecycle(true)?.value ?: true
    val fontSize = viewModel.settingsRepository?.fontSize
        ?.collectAsStateWithLifecycle(16f)?.value ?: 16f

    SettingsDialog(
        isVisible = isVisible,
        initialLoggingEnabled = loggingEnabled,
        initialShowMetadata = showMetadata,
        initialFontSize = fontSize,
        onLoggingChanged = { viewModel.settingsRepository?.setLoggingEnabled(it) },
        onShowMetadataChanged = { viewModel.settingsRepository?.setShowMetadata(it) },
        onFontSizeChanged = { viewModel.settingsRepository?.setFontSize(it) },
        onResetLogs = {
            onDismiss()
            onResetLogsRequested()
        },
        onDismiss = onDismiss
    )
}
