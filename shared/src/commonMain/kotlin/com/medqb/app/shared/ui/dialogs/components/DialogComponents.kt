package com.medqb.app.shared.ui.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Responsive dialog layout tokens.
 */
internal object DialogLayout {
    /** Minimum dialog container width per M3 specification (280dp) */
    val MinWidth: Dp = 280.dp

    /** Maximum dialog container width per M3 specification (560dp) */
    val MaxWidth: Dp = 560.dp

    /** Fraction of screen width for compact viewports (phones) */
    const val CompactWidthFraction: Float = 0.92f

    /** Fraction of screen width for expanded viewports (tablets/desktop) */
    const val ExpandedWidthFraction: Float = 0.65f

    /** Safety margin subtracted from screen height for dialog container max height */
    val MaxHeightInset: Dp = 32.dp

    /** Minimum structural container height for dialog shell */
    val MinContainerHeight: Dp = 200.dp

    /** Viewport height threshold for compact landscape dialog adjustments */
    val CompactHeightThreshold: Dp = 400.dp

    /** Compact vertical padding for list items in landscape mode */
    val CompactItemPadding: Dp = 4.dp

    /** Compact padding for input fields in landscape mode */
    val CompactInputPadding: Dp = 2.dp
}

/**
 * Composition local providing whether the dialog is in compact height mode.
 * Set by [DialogShell] based on the available viewport height.
 */
val LocalDialogCompactMode = compositionLocalOf { false }

/**
 * Base dialog shell with consistent styling for all dialogs.
 *
 * Provides a single [ColumnScope] for content. All dialog children (header, body, actions)
 * should be placed directly in this scope — no intermediate wrapping Column needed.
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center
            ) {
                val isCompactHeight = maxHeight < DialogLayout.CompactHeightThreshold
                CompositionLocalProvider(LocalDialogCompactMode provides isCompactHeight) {
                    Surface(
                        modifier = Modifier
                            .widthIn(min = DialogLayout.MinWidth, max = DialogLayout.MaxWidth)
                            .fillMaxWidth(if (maxWidth >= 600.dp) DialogLayout.ExpandedWidthFraction else DialogLayout.CompactWidthFraction)
                            .heightIn(
                                min = DialogLayout.MinContainerHeight,
                                max = (maxHeight - DialogLayout.MaxHeightInset).coerceAtLeast(DialogLayout.MinContainerHeight)
                            )
                            .clip(MaterialTheme.shapes.extraLarge),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column {
                            content()
                        }
                    }
                }
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
            .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
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
                    modifier = Modifier.padding(top = 4.dp)
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
 * Dialog action buttons using M3-compliant text buttons.
 *
 * Per M3 spec, dialog actions use text buttons (lowest emphasis).
 * Destructive actions use error-colored text.
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (secondaryText != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryText)
            }
        }

        TextButton(
            onClick = onPrimary,
            enabled = primaryEnabled,
        ) {
            Text(
                text = primaryText,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary.copy(
                    alpha = if (primaryEnabled) 1f else 0.38f
                )
            )
        }
    }
}
