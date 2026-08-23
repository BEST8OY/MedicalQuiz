package com.medqb.app.shared.ui.dialogs


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing

/**
 * Dialog for renaming history entries.
 */
@Composable
fun RenameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 50
) {
    val focusManager = LocalFocusManager.current
    val isValid = currentName.isNotBlank() && currentName.length <= maxLength

    DialogShell(onDismiss = onDismiss) {
        DialogHeader(
            title = "Rename entry",
            subtitle = "Give this session a custom title",
            onClose = onDismiss
        )

        OutlinedTextField(
            value = currentName,
            onValueChange = { if (it.length <= maxLength) onNameChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Large, vertical = Spacing.MediumSmall),
            label = { Text("Entry name") },
            singleLine = true,
            isError = currentName.length > maxLength,
            supportingText = {
                Text(
                    text = "${currentName.length} / $maxLength",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (isValid) {
                        onConfirm()
                    }
                }
            ),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        DialogActions(
            primaryText = "Save",
            primaryEnabled = isValid,
            onPrimary = onConfirm,
            secondaryText = "Cancel",
            onSecondary = onDismiss
        )
    }
}
