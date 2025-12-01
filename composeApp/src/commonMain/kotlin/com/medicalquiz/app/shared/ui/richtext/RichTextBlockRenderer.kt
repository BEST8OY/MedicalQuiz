package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as MaterialText
import com.medicalquiz.app.shared.ui.LocalFontSize

@Composable
internal fun RichTextBlockRenderer(
    block: RichTextBlock,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    when (block) {
        is RichTextBlock.Paragraph -> RichTextParagraph(block.text, textAlign = block.textAlign, onLinkClick = onLinkClick, onTooltipClick = onTooltipClick)
        is RichTextBlock.Heading -> RichTextHeading(block, onLinkClick, onTooltipClick)
        is RichTextBlock.BulletList -> RichTextList(
            items = block.items,
            markerProvider = { _ -> "\u2022" },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick
        )
        is RichTextBlock.OrderedList -> RichTextList(
            items = block.items,
            markerProvider = { index -> "${block.start + index}." },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick
        )
        is RichTextBlock.CodeBlock -> RichTextCodeBlock(block)
        is RichTextBlock.Table -> RichTextTable(block, onLinkClick, onTooltipClick)
        is RichTextBlock.AbstractBlock -> AbstractCard(block, onLinkClick, onTooltipClick)
        is RichTextBlock.Media -> RichMedia(block = block, onMediaClick = onMediaClick)
        RichTextBlock.Divider -> HorizontalDivider()
    }
}

@Composable
internal fun RichTextParagraph(
    text: AnnotatedString,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    InteractiveText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = LocalFontSize.current * 1.375f, // assuming 16*1.375=22
            textIndent = TextIndent.None,
            fontSize = LocalFontSize.current
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick
    )
}

@Composable
internal fun InteractiveText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign? = null,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Visible
) {
    // Check if text has any clickable annotations
    val hasAnnotations = remember(text) {
        text.getStringAnnotations(0, text.length).any { 
            it.tag == "URL" || it.tag == "TOOLTIP" 
        }
    }
    
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    
    val textModifier = if (hasAnnotations) {
        modifier.pointerInput(text, onLinkClick, onTooltipClick) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layout ->
                    val offset = layout.getOffsetForPosition(pos)
                    // First check for tooltip annotations
                    text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let {
                        onTooltipClick?.invoke(it.item)
                        return@detectTapGestures
                    }
                    // Then check for URL annotations
                    text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        if (it.item.isNotBlank()) {
                            onLinkClick(it.item)
                        }
                    }
                    // If no annotation at this position, don't consume the click
                    // (gesture will naturally not propagate, but we've only handled actual links)
                }
            }
        }
    } else {
        modifier
    }
    
    BasicText(
        text = text,
        modifier = textModifier,
        style = style.copy(
            color = color,
            textAlign = textAlign ?: TextAlign.Start
        ),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult.value = it }
    )
}

@Composable
private fun RichTextHeading(
    block: RichTextBlock.Heading,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    val baseFontSize = LocalFontSize.current
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(fontSize = baseFontSize * 2.0f)
        2 -> MaterialTheme.typography.headlineSmall.copy(fontSize = baseFontSize * 1.75f)
        3 -> MaterialTheme.typography.titleLarge.copy(fontSize = baseFontSize * 1.5f)
        4 -> MaterialTheme.typography.titleMedium.copy(fontSize = baseFontSize * 1.25f)
        else -> MaterialTheme.typography.titleSmall.copy(fontSize = baseFontSize * 1.125f)
    }
    InteractiveText(
        text = block.text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = block.textAlign,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Visible
    )
}

@Composable
private fun RichTextList(
    items: List<AnnotatedString>,
    markerProvider: (index: Int) -> String,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = markerProvider(index),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
                RichTextParagraph(
                    text = item,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                )
            }
        }
    }
}

@Composable
private fun RichTextCodeBlock(block: RichTextBlock.CodeBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        MaterialText(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun AbstractCard(
    block: RichTextBlock.AbstractBlock,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.title?.let {
                MaterialText(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = LocalFontSize.current * 1.25f),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (block.blocks.isNotEmpty()) {
                RichText(
                    blocks = block.blocks,
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                )
            }
        }
    }
}
