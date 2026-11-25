package com.medqb.app.shared.ui.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Returns the appropriate text style for a table cell based on whether it's a header cell.
 */
@Composable
internal fun tableCellTextStyle(
    isHeaderCell: Boolean,
    cellClassNames: Set<String> = emptySet(),
): TextStyle {
    val base = if (isHeaderCell) {
        MaterialTheme.typography.labelMedium.scaledBy(LocalRichTextScale.current.tableScale)
    } else {
        MaterialTheme.typography.bodySmall.scaledBy(LocalRichTextScale.current.tableScale)
    }
    val isEmphasized = cellClassNames.containsInsensitive("wichtig") || cellClassNames.containsInsensitive("important")
    return if (isEmphasized) base.copy(fontWeight = FontWeight.SemiBold) else base
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
 * Resolves the final text color for a table cell, including "selected"/"important" class tinting.
 * "wichtig" is bold-only (matching [InlineStyle]) without container tinting.
 */
@Composable
internal fun resolveCellTextColor(
    isHeaderCell: Boolean,
    isAbstractRow: Boolean,
    cellClassNames: Set<String>,
    showSelectedHighlight: Boolean = false,
): Color = when {
    showSelectedHighlight && cellClassNames.containsInsensitive("selected") ->
        MaterialTheme.colorScheme.onPrimaryContainer
    cellClassNames.containsInsensitive("important") ->
        MaterialTheme.colorScheme.onTertiaryContainer
    else -> tableCellTextColor(isHeaderCell, isAbstractRow || cellClassNames.containsInsensitive("abstract"))
}

/**
 * Resolves the background color for a table cell, handling "selected" and "important" classes consistently.
 * "wichtig" is bold-only without background tinting (matching [InlineStyle]).
 */
@Composable
internal fun resolveCellBackgroundColor(
    cellClassNames: Set<String>,
    showSelectedHighlight: Boolean = false,
    defaultColor: Color = Color.Transparent,
): Color = when {
    showSelectedHighlight && cellClassNames.containsInsensitive("selected") ->
        MaterialTheme.colorScheme.primaryContainer
    cellClassNames.containsInsensitive("important") ->
        MaterialTheme.colorScheme.tertiaryContainer
    else -> defaultColor
}
