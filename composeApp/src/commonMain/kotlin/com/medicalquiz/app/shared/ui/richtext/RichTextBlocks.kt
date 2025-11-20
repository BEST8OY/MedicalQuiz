package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed interface RichTextBlock {
    data class Paragraph(val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    data class Heading(val level: Int, val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    data class BulletList(val items: List<AnnotatedString>) : RichTextBlock
    data class OrderedList(val items: List<AnnotatedString>, val start: Int) : RichTextBlock
    data class CodeBlock(val text: String) : RichTextBlock
    data class Table(
        val headerRows: List<RichTextTableRow>,
        val bodyRows: List<RichTextTableRow>,
        val columnCount: Int,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data class AbstractBlock(
        val title: AnnotatedString?,
        val blocks: List<RichTextBlock>,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data class Media(
        val source: String,
        val mediaRef: String?,
        val description: String?,
        val width: Int?,
        val height: Int?,
        val alignment: TextAlign,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data object Divider : RichTextBlock
}

data class RichTextTableRow(
    val cells: List<RichTextTableCell>,
    val isHeader: Boolean,
    val classNames: Set<String> = emptySet()
)

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

@Immutable
data class RichTextPalette(
    val importantBackground: Color,
    val importantText: Color,
    val selectedBackground: Color,
    val selectedText: Color,
    val dictionaryText: Color,
    val abstractText: Color
)
