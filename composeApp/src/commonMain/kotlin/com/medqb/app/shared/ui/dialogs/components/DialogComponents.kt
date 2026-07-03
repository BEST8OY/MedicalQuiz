package com.medqb.app.shared.ui.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing

/**
 * Base dialog shell with consistent styling for all dialogs.
 */
@Composable
fun DialogShell(
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = properties
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * Dialog header with title, optional subtitle, and close button.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun DialogHeader(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Inset.Lg, end = Inset.Sm, top = 20.dp, bottom = Spacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.Xs)
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dialog action buttons (primary and secondary).
 */
@Composable
fun DialogActions(
    modifier: Modifier = Modifier,
    primaryText: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Inset.Lg, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (secondaryText != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryText)
            }
        }

        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = if (destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(primaryText)
        }
    }
}
