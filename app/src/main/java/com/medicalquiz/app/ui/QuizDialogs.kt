package com.medicalquiz.app.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.viewmodel.QuizViewModel

/**
 * Settings Dialog Composable
 */
@Composable
fun SettingsDialogComposable(
    isVisible: Boolean,
    viewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onResetLogsRequested: () -> Unit
) {
    if (isVisible) {
        val loggingEnabled = viewModel.settingsRepository?.isLoggingEnabled?.collectAsStateWithLifecycle(false)?.value ?: false
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { },
            text = {
                SettingsDialog(
                    initialLoggingEnabled = loggingEnabled,
                    onLoggingChanged = { enabled ->
                        viewModel.settingsRepository?.setLoggingEnabled(enabled)
                        if (!enabled) viewModel.clearPendingLogsBuffer()
                    },
                    onResetLogs = {
                        onDismiss()
                        onResetLogsRequested()
                    },
                    onDismiss = onDismiss
                )
            }
        )
    }
}

/**
 * Jump to Question Dialog Composable
 */
@Composable
fun JumpToDialogComposable(
    isVisible: Boolean,
    totalQuestions: Int,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible && totalQuestions > 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { },
            text = {
                JumpToQuestionDialog(
                    totalQuestions = totalQuestions,
                    currentQuestionIndex = currentIndex,
                    onJumpTo = onJumpTo,
                    onDismiss = onDismiss
                )
            }
        )
    }
}

/**
 * Error Dialog Composable
 */
@Composable
fun ErrorDialogComposable(
    errorDialog: Pair<String, String>?,
    onDismiss: () -> Unit
) {
    if (errorDialog != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            },
            title = { Text(errorDialog.first) },
            text = { Text(errorDialog.second) }
        )
    }
}

/**
 * Reset Logs Confirmation Dialog Composable
 */
@Composable
fun ResetLogsConfirmationDialogComposable(
    isVisible: Boolean,
    activity: AppCompatActivity?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Reset log history") },
            text = { Text("This will permanently delete all stored answer logs. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    (activity as? com.medicalquiz.app.QuizActivity)?.onResetLogsConfirmed()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
