package com.medqb.app.shared.ui.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Returns the appropriate text style for a table cell based on whether it's a header cell.
 */
@Composable
internal fun tableCellTextStyle(isHeaderCell: Boolean): TextStyle =
    if (isHeaderCell) {
        MaterialTheme.typography.labelMedium.scaledBy(LocalRichTextScale.current.tableScale)
    } else {
        MaterialTheme.typography.bodySmall.scaledBy(LocalRichTextScale.current.tableScale)
    }

/**
 * Base text color for table cells (header vs abstract vs default).
 */
@Composable
internal fun tableCellTextColor(isHeaderCell: Boolean, isAbstractClass: Boolean = false) =
    when {
        isHeaderCell -> MaterialTheme.colorScheme.onSurface
        isAbstractClass -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

/**
 * Resolves the final text color for a table cell, including "selected"/"wichtig" class tinting.
 * Shared by both RichTextTable and HighlightableTable to prevent color drift.
 */
@Composable
internal fun resolveCellTextColor(
    isHeaderCell: Boolean,
    isAbstractRow: Boolean,
    cellClassNames: Set<String>
): Color = when {
    cellClassNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.onSecondaryContainer
    cellClassNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> tableCellTextColor(isHeaderCell, isAbstractRow || cellClassNames.containsInsensitive("abstract"))
}
