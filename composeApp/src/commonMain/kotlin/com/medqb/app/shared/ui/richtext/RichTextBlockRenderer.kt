package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as MaterialText
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

@Composable
internal fun RichTextBlockRenderer(
    block: RichTextBlock,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    when (block) {
        is RichTextBlock.Paragraph -> RichTextParagraph(
            block.text,
            textAlign = block.textAlign,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
        )
        is RichTextBlock.Heading -> RichTextHeading(block, onLinkClick, onTooltipClick)
        is RichTextBlock.BulletList -> RichTextList(
            items = block.items,
            markerProvider = { _ -> "\u2022" },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
        )
        is RichTextBlock.OrderedList -> RichTextList(
            items = block.items,
            markerProvider = { index -> "${block.start + index}." },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
        )
        is RichTextBlock.CodeBlock -> RichTextCodeBlock(block)
        is RichTextBlock.Table -> RichTextTable(block, onLinkClick, onTooltipClick)
        is RichTextBlock.AbstractBlock -> AbstractCard(block, onLinkClick, onMediaClick, onTooltipClick)
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    val proseScale = LocalRichTextScale.current.proseScale
    val scaledBodyMedium = MaterialTheme.typography.bodyMedium.scaledBy(proseScale)

    InteractiveText(
        text = text,
        modifier = modifier,
        style = scaledBodyMedium.copy(
            lineHeight = scaledBodyMedium.fontSize * LINE_HEIGHT_MULTIPLIER,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            ),
            textIndent = TextIndent.None,
        ),
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
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
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

    // Use rememberUpdatedState so pointerInput keys only on content, not callback identity.
    // This prevents gesture coroutine restart if upstream creates fresh lambdas on recomposition.
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnTooltipClick by rememberUpdatedState(onTooltipClick)

    val textModifier = if (hasAnnotations) {
        modifier.pointerInput(text) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layout ->
                    val offset = layout.getOffsetForPosition(pos)
                    handleAnnotatedTextTap(
                        text = text,
                        offset = offset,
                        onLinkClick = currentOnLinkClick,
                        onTooltipClick = currentOnTooltipClick
                    )
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
            color = if (color != Color.Unspecified) color else if (style.color != Color.Unspecified) style.color else LocalContentColor.current,
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    val richTextScale = LocalRichTextScale.current
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }.scaledBy(richTextScale.proseScale)
    InteractiveText(
        text = block.text,
        style = style,
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    val richTextScale = LocalRichTextScale.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = markerProvider(index),
                    style = MaterialTheme.typography.bodyMedium.scaledBy(richTextScale.proseScale),
                    modifier = Modifier.padding(end = Inset.Sm),
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
    val richTextScale = LocalRichTextScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        MaterialText(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ).scaledBy(richTextScale.proseScale),
            modifier = Modifier.padding(Inset.Sm)
        )
    }
}

@Composable
private fun AbstractCard(
    block: RichTextBlock.AbstractBlock,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    val richTextScale = LocalRichTextScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Inset.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            block.title?.let {
                MaterialText(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.scaledBy(richTextScale.proseScale),
                )
            }
            if (block.blocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    block.blocks.forEach { childBlock ->
                        RichTextBlockRenderer(
                            block = childBlock,
                            onLinkClick = onLinkClick,
                            onMediaClick = onMediaClick,
                            onTooltipClick = onTooltipClick
                        )
                    }
                }
            }
        }
    }
}
