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
    val fontScalePreference = viewModel.settingsRepository?.fontScalePreference
        ?.collectAsStateWithLifecycle(null)?.value

    SettingsDialog(
        isVisible = isVisible,
        initialLoggingEnabled = loggingEnabled,
        initialShowMetadata = showMetadata,
        initialFontScalePreference = fontScalePreference,
        onLoggingChanged = { viewModel.settingsRepository?.setLoggingEnabled(it) },
        onShowMetadataChanged = { viewModel.settingsRepository?.setShowMetadata(it) },
        onFontScalePreferenceChanged = { viewModel.settingsRepository?.setFontScalePreference(it) },
        onResetLogs = {
            onDismiss()
            onResetLogsRequested()
        },
        onDismiss = onDismiss
    )
}
