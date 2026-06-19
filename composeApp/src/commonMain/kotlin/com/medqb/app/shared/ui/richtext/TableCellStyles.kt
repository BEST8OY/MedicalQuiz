package com.medqb.app.shared.ui.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * Returns the appropriate text style for a table cell based on whether it's a header cell.
 * Used by both RichTextTable and HighlightableTable for consistent styling.
 *
 * @param isHeaderCell Whether this cell should be styled as a header
 * @return TextStyle with smaller font size appropriate for table content
 */
@Composable
internal fun tableCellTextStyle(isHeaderCell: Boolean): TextStyle =
    if (isHeaderCell) {
        MaterialTheme.typography.labelMedium.scaledBy(LocalRichTextScale.current.tableScale)
    } else {
        MaterialTheme.typography.bodySmall.scaledBy(LocalRichTextScale.current.tableScale)
    }

/**
 * Returns the appropriate text color for a table cell based on its properties.
 *
 * @param isHeaderCell Whether this cell is a header
 * @param isAbstractClass Whether the cell has abstract class styling
 * @return Color from the MaterialTheme color scheme
 */
@Composable
internal fun tableCellTextColor(isHeaderCell: Boolean, isAbstractClass: Boolean = false) =
    when {
        isHeaderCell -> MaterialTheme.colorScheme.onSecondaryContainer
        isAbstractClass -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
