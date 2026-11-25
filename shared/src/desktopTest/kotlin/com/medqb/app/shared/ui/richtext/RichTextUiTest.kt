package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.ui.screens.quiz.AnswerOptions
import com.medqb.app.shared.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RichTextUiTest {

    @Test
    fun answerOptionsAllowsSelectingAnswerWhenTappingPlainTextNextToTooltip() = runComposeUiTest {
        var selectedId: Long? = null

        val answer = Answer(
            answerId = 42L,
            answerText = "<p>Start <span data-tooltip-text=\"Elevated Na\">hypernatremia</span></p>",
            correctPercentage = null,
            qId = 1L
        )

        setContent {
            AppTheme {
                AnswerOptions(
                    answers = listOf(answer),
                    sanitizedAnswers = mapOf(42L to answer.answerText),
                    selectedAnswerId = null,
                    correctAnswerId = null,
                    answerSubmitted = false,
                    answerPercentages = mapOf(42L to null),
                    onAnswerSelected = { selectedId = it },
                    onLinkClick = {},
                    onMediaClick = {},
                )
            }
        }

        // Tapping plain text "Start" inside an answer with a tooltip must select the answer
        val textNode = onNodeWithText("Start", substring = true, useUnmergedTree = true).assertIsDisplayed()
        val semantics = textNode.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        semantics.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first { it.layoutInput.text.text.contains("Start") }
        val startBox = layout.getBoundingBox(1)

        textNode.performTouchInput {
            click(Offset(startBox.left + 2f, (startBox.top + startBox.bottom) / 2f))
        }
        waitForIdle()
        assertEquals(42L, selectedId)

        // Reset and tap trailing empty whitespace past line end: should select answer, NOT open tooltip
        selectedId = null
        val lineRight = layout.getLineRight(0)
        if (semantics.size.width > lineRight + 16f) {
            textNode.performTouchInput {
                click(Offset(lineRight + 12f, (startBox.top + startBox.bottom) / 2f))
            }
            waitForIdle()
            assertEquals(42L, selectedId)
            onNodeWithText("Elevated Na").assertDoesNotExist()
        }

        // Tapping the tooltip word itself opens the tooltip sheet and does NOT select the answer
        selectedId = null
        val tooltipStart = layout.layoutInput.text.text.indexOf("hypernatremia")
        val tooltipBox = layout.getBoundingBox(tooltipStart + 2)
        textNode.performTouchInput {
            click(Offset((tooltipBox.left + tooltipBox.right) / 2f, (tooltipBox.top + tooltipBox.bottom) / 2f))
        }
        waitForIdle()
        assertEquals(null, selectedId)
        onNodeWithText("Elevated Na").assertIsDisplayed()
    }

    @Test
    fun richTextPreservesTextAlignAndGatesTableSelectedHighlight() = runComposeUiTest {
        var showSelected by mutableStateOf(false)
        val html = """
            <p align="center">Centered prompt text</p>
            <table>
                <tr><td class="selected">122 mEq/L</td></tr>
            </table>
        """.trimIndent()

        setContent {
            AppTheme {
                RichText(
                    html = html,
                    showSelectedHighlight = showSelected,
                    highlightConfig = RichTextHighlightConfig(
                        section = HighlightSection.QUESTION,
                        highlights = emptyList(),
                        onHighlightAdd = { _, _, _, _, _, _ -> },
                        onHighlightRemove = {},
                        onHighlightColorChange = { _, _ -> },
                    ),
                )
            }
        }

        val paraNode = onNodeWithText("Centered prompt text").assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        paraNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(TextAlign.Center, layouts.first().layoutInput.style.textAlign)

        val cellBefore = onNodeWithText("122 mEq/L").assertIsDisplayed().fetchSemanticsNode()
        val cellLayoutsBefore = mutableListOf<TextLayoutResult>()
        cellBefore.config[SemanticsActions.GetTextLayoutResult].action?.invoke(cellLayoutsBefore)
        val colorBefore = cellLayoutsBefore.first().layoutInput.style.color

        showSelected = true
        waitForIdle()
        val cellAfter = onNodeWithText("122 mEq/L").assertIsDisplayed().fetchSemanticsNode()
        val cellLayoutsAfter = mutableListOf<TextLayoutResult>()
        cellAfter.config[SemanticsActions.GetTextLayoutResult].action?.invoke(cellLayoutsAfter)
        val colorAfter = cellLayoutsAfter.first().layoutInput.style.color
        assertNotEquals(colorBefore, colorAfter)
    }

    @Test
    fun tallRowspanAnchorCellIsNotClippedAndExpandsRowHeight() = runComposeUiTest {
        val html = """
            <table>
                <tr>
                    <td rowspan="2">Line1<br/>Line2<br/>Line3<br/>Line4<br/>Line5<br/>Line6</td>
                    <td>ShortA</td>
                </tr>
                <tr>
                    <td>ShortB</td>
                </tr>
            </table>
        """.trimIndent()

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(400.dp, 400.dp)) {
                    RichText(html = html)
                }
            }
        }

        val anchorTextNode = onNodeWithText("Line1", substring = true).assertIsDisplayed()
        val anchorSemantics = anchorTextNode.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        anchorSemantics.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val expectedTextHeight = layouts.first().size.height.toFloat()

        // Rendered bounds height must match full unclipped 6-line text layout height
        assertTrue(
            anchorSemantics.boundsInRoot.height >= expectedTextHeight - 1f,
            "Expected anchor height (${anchorSemantics.boundsInRoot.height}) >= layout height ($expectedTextHeight)"
        )

        val shortBNode = onNodeWithText("ShortB").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(
            anchorSemantics.boundsInRoot.bottom >= shortBNode.boundsInRoot.bottom - 1f,
            "Expected tall rowspan anchor bottom (${anchorSemantics.boundsInRoot.bottom}) to reach ShortB bottom (${shortBNode.boundsInRoot.bottom})"
        )
    }
}
