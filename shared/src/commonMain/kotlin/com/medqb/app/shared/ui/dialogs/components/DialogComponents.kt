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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medqb.app.shared.ui.adaptive.AdaptiveLayoutInfo
import com.medqb.app.shared.ui.adaptive.LocalAdaptiveLayoutInfo

/**
 * Responsive dialog layout tokens.
 */
internal object DialogLayout {
    /** Minimum dialog container width per M3 specification (280dp) */
    val MinWidth: Dp = 280.dp

    /** Maximum dialog container width per M3 specification (560dp) */
    val MaxWidth: Dp = 560.dp

    /** Safety margin subtracted from screen height for dialog container max height */
    val MaxHeightInset: Dp = 32.dp

    /** Minimum structural container height for dialog shell */
    val MinContainerHeight: Dp = 200.dp

    /** Fallback minimum height when IME + landscape shrinks viewport below MinContainerHeight */
    val AbsoluteMinHeight: Dp = 120.dp
}

/**
 * Base dialog shell with consistent styling for all dialogs.
 *
 * Provides a single [ColumnScope] for content. All dialog children (header, body, actions)
 * should be placed directly in this scope — no intermediate wrapping Column needed.
 */
@Composable
fun DialogShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = DialogLayout.MaxWidth,
    minHeight: Dp = DialogLayout.MinContainerHeight,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = contentColorFor(containerColor),
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val parentAdaptive = LocalAdaptiveLayoutInfo.current
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
                // Dialogs run in their own window whose available height shrinks when the IME opens.
                val dialogHeightClass = remember(maxWidth, maxHeight) {
                    AdaptiveLayoutInfo.fromDimensions(maxWidth, maxHeight).heightClass
                }
                val dialogAdaptive = remember(parentAdaptive, dialogHeightClass) {
                    parentAdaptive.copy(heightClass = dialogHeightClass)
                }
                val availableMaxHeight = (maxHeight - DialogLayout.MaxHeightInset)
                    .coerceAtLeast(DialogLayout.AbsoluteMinHeight)
                val resolvedMinHeight = minHeight.coerceAtMost(availableMaxHeight)
                val targetWidth = (this.maxWidth * dialogAdaptive.dialogWidthFraction)
                    .coerceIn(DialogLayout.MinWidth, maxWidth)
                    .coerceAtMost(this.maxWidth)

                CompositionLocalProvider(LocalAdaptiveLayoutInfo provides dialogAdaptive) {
                    Surface(
                        modifier = modifier
                            .width(targetWidth)
                            .heightIn(
                                min = resolvedMinHeight,
                                max = availableMaxHeight
                            )
                            .clip(MaterialTheme.shapes.extraLarge),
                        color = containerColor,
                        contentColor = contentColor,
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
    val isCompactHeight = LocalAdaptiveLayoutInfo.current.isCompactHeight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 24.dp,
                end = 12.dp,
                top = if (isCompactHeight) 12.dp else 20.dp,
                bottom = if (isCompactHeight) 8.dp else 12.dp,
            ),
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
    val isCompactHeight = LocalAdaptiveLayoutInfo.current.isCompactHeight
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 12.dp else 20.dp),
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
            colors = if (destructive) {
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                )
            } else {
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                )
            }
        ) {
            Text(text = primaryText)
        }
    }
}
