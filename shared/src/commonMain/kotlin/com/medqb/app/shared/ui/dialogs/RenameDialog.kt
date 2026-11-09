package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell

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
                .padding(horizontal = 24.dp, vertical = 12.dp),
            label = { Text("Entry name") },
            singleLine = true,
            isError = currentName.isBlank(),
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (currentName.isBlank()) {
                        Text(
                            text = "Name cannot be empty",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = "${currentName.length} / $maxLength",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
