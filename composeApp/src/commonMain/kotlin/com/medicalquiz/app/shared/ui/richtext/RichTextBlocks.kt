package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sealed interface representing different types of rich text content blocks.
 * Each implementation corresponds to a specific HTML element type or semantic structure.
 */
sealed interface RichTextBlock {
    /** A paragraph of text with optional alignment. */
    data class Paragraph(val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    
    /** A heading with level (1-6) and optional alignment. */
    data class Heading(val level: Int, val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    
    /** An unordered (bullet) list. */
    data class BulletList(val items: List<AnnotatedString>) : RichTextBlock
    
    /** An ordered (numbered) list with configurable start index. */
    data class OrderedList(val items: List<AnnotatedString>, val start: Int) : RichTextBlock
    
    /** A code block with monospaced text. */
    data class CodeBlock(val text: String) : RichTextBlock
    
    /** A table with header and body rows, supporting rowspan/colspan. */
    data class Table(
        val headerRows: List<RichTextTableRow>,
        val bodyRows: List<RichTextTableRow>,
        val columnCount: Int,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    
    /** An abstract/summary block with optional title and nested content. */
    data class AbstractBlock(
        val title: AnnotatedString?,
        val blocks: List<RichTextBlock>,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    
    /** A media element (image) with source, description, and layout properties. */
    data class Media(
        val source: String,
        val mediaRef: String?,
        val description: String?,
        val width: Int?,
        val height: Int?,
        val alignment: TextAlign,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    
    /** A horizontal divider line. */
    data object Divider : RichTextBlock
}

/**
 * Represents a row in a table.
 * 
 * @param cells The cells in this row
 * @param isHeader Whether this is a header row
 * @param classNames CSS class names for styling
 */
data class RichTextTableRow(
    val cells: List<RichTextTableCell>,
    val isHeader: Boolean,
    val classNames: Set<String> = emptySet()
)

/**
 * Represents a cell in a table row.
 * 
 * @param text The styled text content of the cell
 * @param columnSpan Number of columns this cell spans
 * @param rowSpan Number of rows this cell spans
 * @param alignment Text alignment within the cell
 * @param isHeader Whether this is a header cell
 * @param classNames CSS class names for styling
 * @param width Optional width as a fraction or pixel value
 * @param paddingStart Left padding in Dp
 */
data class RichTextTableCell(
    val text: AnnotatedString,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val alignment: TextAlign = TextAlign.Start,
    val isHeader: Boolean = false,
    val classNames: Set<String> = emptySet(),
    val width: Float? = null,
    val paddingStart: Dp = 0.dp
)

/**
 * Color palette for rich text styling.
 * 
 * @param importantBackground Background color for important/highlighted content
 * @param importantText Text color for important/highlighted content
 * @param selectedBackground Background color for selected content
 * @param selectedText Text color for selected content
 * @param dictionaryText Text color for dictionary terms and tooltips
 * @param abstractText Text color for abstract/summary sections
 */
@Immutable
data class RichTextPalette(
    val importantBackground: Color,
    val importantText: Color,
    val selectedBackground: Color,
    val selectedText: Color,
    val dictionaryText: Color,
    val abstractText: Color
)
