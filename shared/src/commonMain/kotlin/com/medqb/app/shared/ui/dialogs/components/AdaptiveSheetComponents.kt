package com.medqb.app.shared.ui.dialogs.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.adaptive.LocalAdaptiveLayoutInfo

/**
 * Shared modal bottom sheet container with consistent drag handle, surface colors,
 * responsive max width, and automatic partial-expansion skipping on compact-height viewports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedQBModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = LocalAdaptiveLayoutInfo.current.isCompactHeight,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = contentColorFor(containerColor),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = if (skipPartiallyExpanded) {
            setOf(SheetValue.Hidden, SheetValue.Expanded)
        } else {
            setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
        },
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAdaptiveLayoutInfo.current.isExpandedWidth) {
        DialogShell(
            onDismiss = onDismissRequest,
            modifier = modifier,
            maxWidth = 600.dp,
            minHeight = 0.dp,
            containerColor = containerColor,
            contentColor = contentColor,
        ) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                content()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
            containerColor = containerColor,
            contentColor = contentColor,
            scrimColor = scrimColor,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            },
            content = content,
        )
    }
}

/**
 * Filter sheet wrapper over [MedQBModalBottomSheet] that always skips partial expansion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MedQBModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        skipPartiallyExpanded = true,
        content = content,
    )
}

/**
 * Standard title + subtitle header for filter bottom sheets.
 */
@Composable
fun BottomSheetHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Standard Cancel / Apply action button row for bottom sheets, with an optional [leadingContent] slot.
 */
@Composable
fun BottomSheetActions(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    applyEnabled: Boolean = true,
    applyText: String = "Apply",
    cancelModifier: Modifier = Modifier,
    applyModifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (!LocalAdaptiveLayoutInfo.current.isExpandedWidth && navBarBottom > 0.dp) {
        8.dp
    } else {
        16.dp
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottomPadding),
        horizontalArrangement = if (leadingContent != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = cancelModifier,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onApply,
                enabled = applyEnabled,
                modifier = applyModifier,
            ) {
                Text(applyText)
            }
        }
    }
}
