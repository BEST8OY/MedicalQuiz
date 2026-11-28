package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.ui.richtext.interaction.handleAnnotatedTextTap
import com.medqb.app.shared.ui.richtext.interaction.isPositionWithinTextBounds
import com.medqb.app.shared.ui.richtext.styling.resolveBlockHighlights
import androidx.compose.material3.Text as MaterialText

private val OPTION_HEADING_REGEX = Regex("""^\[\s*[A-Za-z0-9]\s*\]\s*\[\s*\d+%\s*\]""")

@Composable
internal fun RichTextBlockRenderer(
    block: RichTextBlock,
    blockIndex: Int = 0,
    mediaOwnerPrefix: String = "",
    showSelectedHighlight: Boolean = false,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String, String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    when (block) {
        is RichTextBlock.Paragraph -> RichTextParagraph(
            text = block.text,
            blockKey = "p:$blockIndex",
            textAlign = block.textAlign,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.Heading -> RichTextHeading(
            block = block,
            blockKey = "h:$blockIndex",
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.BulletList -> RichTextList(
            items = block.items,
            blockIndex = blockIndex,
            markerProvider = { _ -> "\u2022" },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.OrderedList -> RichTextList(
            items = block.items,
            blockIndex = blockIndex,
            markerProvider = { index -> "${block.start + index}." },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.CodeBlock -> RichTextCodeBlock(
            block = block,
            blockKey = "code:$blockIndex",
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.Table -> RichTextTable(
            block = block,
            blockIndex = blockIndex,
            showSelectedHighlight = showSelectedHighlight,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            highlightConfig = highlightConfig,
        )
        is RichTextBlock.Media -> RichMedia(
            block = block,
            onMediaClick = onMediaClick,
            blockIndex = blockIndex,
            mediaOwnerPrefix = mediaOwnerPrefix,
        )
        RichTextBlock.Divider -> HorizontalDivider()
    }
}

@Composable
internal fun HighlightableRichTextContent(
    text: AnnotatedString,
    blockKey: String,
    highlightConfig: RichTextHighlightConfig?,
    style: TextStyle,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onTooltipClick: ((RichTextTooltipContent) -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Visible,
) {
    if (highlightConfig != null) {
        val blockHighlights = remember(highlightConfig.highlights, blockKey, text.text) {
            resolveBlockHighlights(
                blockText = text.text,
                blockKey = blockKey,
                highlights = highlightConfig.highlights,
            )
        }
        val mappedAdd = remember(blockKey, highlightConfig.section, highlightConfig.onHighlightAdd) {
            { localStart: Int, localEnd: Int, selected: String, chosenColor: HighlightColor ->
                highlightConfig.onHighlightAdd(
                    highlightConfig.section,
                    blockKey,
                    localStart,
                    localEnd,
                    selected,
                    chosenColor,
                )
            }
        }
        SelectableHighlightText(
            text = text,
            highlights = blockHighlights,
            modifier = modifier,
            textStyle = if (textAlign != null) style.copy(textAlign = textAlign) else style,
            color = color,
            onHighlightAdd = mappedAdd,
            onHighlightRemove = highlightConfig.onHighlightRemove,
            onHighlightColorChange = highlightConfig.onHighlightColorChange,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            onShowSnackbar = highlightConfig.onShowSnackbar,
        )
    } else {
        InteractiveText(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            textAlign = textAlign,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

@Composable
internal fun RichTextParagraph(
    text: AnnotatedString,
    blockKey: String = "p:0",
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val proseScale = LocalRichTextScale.current.proseScale
    val scaledBodyMedium = MaterialTheme.typography.bodyMedium.scaledBy(proseScale)
    val paragraphStyle = scaledBodyMedium.copy(
        lineHeight = scaledBodyMedium.fontSize * LINE_HEIGHT_MULTIPLIER,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        ),
        textIndent = TextIndent.None,
        textAlign = textAlign,
    )

    HighlightableRichTextContent(
        text = text,
        blockKey = blockKey,
        highlightConfig = highlightConfig,
        style = paragraphStyle,
        textAlign = textAlign,
        modifier = modifier,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
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
    val hasAnnotations = remember(text) {
        text.getStringAnnotations(0, text.length).any {
            it.tag == "URL" || it.tag == "TOOLTIP"
        }
    }

    val layoutResult = remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnTooltipClick by rememberUpdatedState(onTooltipClick)

    val textModifier = if (hasAnnotations) {
        modifier.pointerInput(text) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = true)
                val up = waitForUpOrCancellation()
                if (up != null && !up.isConsumed) {
                    val layout = layoutResult.value
                    if (layout != null && isPositionWithinTextBounds(layout, up.position)) {
                        val offset = layout.getOffsetForPosition(up.position)
                        val handled = handleAnnotatedTextTap(
                            text = text,
                            offset = offset,
                            onLinkClick = currentOnLinkClick,
                            onTooltipClick = currentOnTooltipClick
                        )
                        if (handled) {
                            up.consume()
                        }
                    }
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
            textAlign = textAlign ?: style.textAlign
        ),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult.value = it }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RichTextHeading(
    block: RichTextBlock.Heading,
    blockKey: String,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val richTextScale = LocalRichTextScale.current
    val headingText = block.text.text.trim()
    val isCorrectAnswerHeading = block.level == 4 && headingText.startsWith("Correct Answer Is", ignoreCase = true)
    val isOptionHeading = block.level == 4 && OPTION_HEADING_REGEX.matches(headingText)

    val style = when {
        block.level == 1 -> MaterialTheme.typography.headlineMedium
        block.level == 2 -> MaterialTheme.typography.headlineSmall
        block.level == 3 -> MaterialTheme.typography.titleLarge
        isCorrectAnswerHeading || isOptionHeading -> MaterialTheme.typography.titleMediumEmphasized
        block.level == 4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }.scaledBy(richTextScale.proseScale)

    val color = when {
        isCorrectAnswerHeading -> MaterialTheme.colorScheme.primary
        isOptionHeading -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Unspecified
    }

    HighlightableRichTextContent(
        text = block.text,
        blockKey = blockKey,
        highlightConfig = highlightConfig,
        style = if (color != Color.Unspecified) style.copy(color = color) else style,
        color = color,
        textAlign = block.textAlign,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun RichTextList(
    items: List<AnnotatedString>,
    blockIndex: Int,
    markerProvider: (index: Int) -> String,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val richTextScale = LocalRichTextScale.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = markerProvider(index),
                    style = MaterialTheme.typography.bodyMedium.scaledBy(richTextScale.proseScale),
                    modifier = Modifier.padding(end = 12.dp),
                )
                RichTextParagraph(
                    text = item,
                    blockKey = "li:$blockIndex:$index",
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick,
                    highlightConfig = highlightConfig,
                )
            }
        }
    }
}

@Composable
private fun RichTextCodeBlock(
    block: RichTextBlock.CodeBlock,
    blockKey: String,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val richTextScale = LocalRichTextScale.current
    val codeStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
    ).scaledBy(richTextScale.proseScale)
    val annotatedCode = remember(block.text) { AnnotatedString(block.text) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        HighlightableRichTextContent(
            text = annotatedCode,
            blockKey = blockKey,
            highlightConfig = highlightConfig,
            style = codeStyle,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}
