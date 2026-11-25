package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.richtext.toolbar.HighlightFloatingToolbar
import com.medqb.app.shared.ui.richtext.toolbar.HighlightToolbarMode
import com.medqb.app.shared.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HighlightToolbarUiTest {

    @Test
    fun highlightToolbarFitsIn320dpAndActiveDotRemovesHighlight() = runComposeUiTest {
        var maxWidthPx = 0f
        var removedId: Long? = null
        var isEditMode by mutableStateOf(false)

        val sampleHighlight = TextHighlight(
            id = 7L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            startOffset = 0,
            endOffset = 10,
            highlightedText = "Hyponatremia",
            color = HighlightColor.BLUE,
            createdAt = 1L
        )

        setContent {
            maxWidthPx = with(LocalDensity.current) { 320.dp.toPx() }
            AppTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    HighlightFloatingToolbar(
                        mode = if (isEditMode) {
                            HighlightToolbarMode.Edit(
                                highlight = sampleHighlight,
                                overlappingTooltip = RichTextTooltipContent("Hyponatremia", "Low Na"),
                            )
                        } else {
                            HighlightToolbarMode.Create("Hyponatremia")
                        },
                        onColorSelect = { color ->
                            if (isEditMode && color == sampleHighlight.color) {
                                removedId = sampleHighlight.id
                            }
                        },
                        onCopy = {},
                        onLookup = {},
                        onDelete = { removedId = sampleHighlight.id },
                        onOpenTooltip = {},
                    )
                }
            }
        }

        val copyNode = onNodeWithText("Copy").assertIsDisplayed()
        onNodeWithText("Look up").assertIsDisplayed()
        assertTrue(copyNode.fetchSemanticsNode().boundsInRoot.right <= maxWidthPx + 1f)

        isEditMode = true
        waitForIdle()
        onNodeWithContentDescription("Delete highlight").assertIsDisplayed()
        onNodeWithText("Definition").assertIsDisplayed()
        val activeDot = onNodeWithContentDescription("Blue highlight, selected, tap to remove").assertIsDisplayed()
        assertTrue(activeDot.fetchSemanticsNode().boundsInRoot.right <= maxWidthPx + 1f)
        activeDot.performClick()
        assertEquals(7L, removedId)
    }

    @Test
    fun touchLongPressShowsHandlesAndLookupDismissesPopupForSnackbar() = runComposeUiTest {
        var snackbarCount = 0

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(400.dp, 600.dp).padding(top = 180.dp)) {
                    RichText(
                        html = "<p>Hyponatremia is a common electrolyte disorder</p>",
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = emptyList(),
                            onHighlightAdd = { _, _, _, _, _, _ -> },
                            onHighlightRemove = {},
                            onHighlightColorChange = { _, _ -> },
                            onShowSnackbar = { msg ->
                                if (msg is SnackbarMessage.Action) snackbarCount++
                            },
                        ),
                    )
                }
            }
        }

        val textNode = onNodeWithText("Hyponatremia", substring = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val wordBox = layouts.first().getBoundingBox(3)

        textNode.performTouchInput {
            longClick(Offset(wordBox.left + 2f, (wordBox.top + wordBox.bottom) / 2f))
        }
        waitForIdle()

        onNodeWithTag("selection_handle_start").assertIsDisplayed()
        onNodeWithTag("selection_handle_end").assertIsDisplayed()
        onNodeWithText("Copy").assertIsDisplayed()
        val lookupButton = onNodeWithText("Look up").assertIsDisplayed()

        // Tapping Look up on Desktop (TextIntentLauncher returns false) dismisses popup and triggers snackbar
        lookupButton.performClick()
        waitForIdle()
        assertEquals(1, snackbarCount)
        onNodeWithTag("selection_handle_start").assertDoesNotExist()
    }

    @Test
    fun mouseDragAndDoubleClickOpenToolbarAndTextChangeResetsState() = runComposeUiTest {
        var html by mutableStateOf("<p>Hyperkalemia causes peaked T waves</p>")

        setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize().padding(top = 180.dp)) {
                    RichText(
                        html = html,
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
        }

        val textNode = onNodeWithText("Hyperkalemia", substring = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val startBox = layout.getBoundingBox(0)
        val endBox = layout.getBoundingBox(10)

        // Mouse drag selection
        textNode.performMouseInput {
            moveTo(Offset(startBox.left + 1f, (startBox.top + startBox.bottom) / 2f))
            press()
            moveBy(Offset(endBox.right - startBox.left, 0f))
            release()
        }
        waitForIdle()
        onNodeWithText("Copy").assertIsDisplayed()
        // Mouse selection should NOT show touch teardrop handles
        onNodeWithTag("selection_handle_start").assertDoesNotExist()

        // Switching text resets selection state cleanly
        html = "<p>Next question stem</p>"
        waitForIdle()
        onNodeWithText("Copy").assertDoesNotExist()

        // Mouse double-click selects word and opens toolbar
        val nextNode = onNodeWithText("Next question stem").assertIsDisplayed()
        val nextLayouts = mutableListOf<TextLayoutResult>()
        nextNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(nextLayouts)
        val nextBox = nextLayouts.first().getBoundingBox(6)
        nextNode.performMouseInput {
            doubleClick(Offset(nextBox.left + 2f, (nextBox.top + nextBox.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithText("Copy").assertIsDisplayed()
    }

    @Test
    fun draggingTouchSelectionHandlesExpandsRangeWithoutClosingToolbar() = runComposeUiTest {
        var addedText: String? = null

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(500.dp, 600.dp).padding(top = 180.dp, start = 24.dp, end = 24.dp)) {
                    RichText(
                        html = "<p>Acute hyponatremia causes confusion</p>",
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = emptyList(),
                            onHighlightAdd = { _, _, _, _, text, _ -> addedText = text },
                            onHighlightRemove = {},
                            onHighlightColorChange = { _, _ -> },
                        ),
                    )
                }
            }
        }

        val textNode = onNodeWithText("Acute hyponatremia causes confusion").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val middleCharBox = layout.getBoundingBox(8) // inside "hyponatremia"

        // Long-press "hyponatremia"
        textNode.performTouchInput {
            longClick(Offset(middleCharBox.left + 2f, (middleCharBox.top + middleCharBox.bottom) / 2f))
        }
        waitForIdle()

        val startHandle = onNodeWithTag("selection_handle_start").assertIsDisplayed()
        val endHandle = onNodeWithTag("selection_handle_end").assertIsDisplayed()
        onNodeWithText("Copy").assertIsDisplayed()

        // Drag end handle rightward across " causes"
        val endTargetBox = layout.getBoundingBox(24) // inside "causes"
        val currentEndRight = layout.getBoundingBox(17).right
        val deltaRight = (endTargetBox.right - currentEndRight).coerceAtLeast(40f)
        endHandle.performTouchInput {
            down(center)
            moveBy(Offset(deltaRight, 0f))
            up()
        }
        waitForIdle()

        // Drag start handle leftward across "Acute "
        val acuteStartLeft = layout.getBoundingBox(0).left
        val currentStartLeft = layout.getBoundingBox(6).left
        val deltaLeft = (currentStartLeft - acuteStartLeft).coerceAtLeast(30f)
        startHandle.performTouchInput {
            down(center)
            moveBy(Offset(-deltaLeft, 0f))
            up()
        }
        waitForIdle()

        // Toolbar should still be open and ready to highlight the expanded range
        onNodeWithContentDescription("Yellow highlight").assertIsDisplayed().performClick()
        waitForIdle()
        val capturedText = addedText
        assertTrue(
            capturedText != null && capturedText.contains("Acute") && capturedText.contains("causes"),
            "Expected expanded selection containing 'Acute' and 'causes', got '$capturedText'"
        )
    }

    @Test
    fun adjacentHighlightDoesNotHijackNeighboringLinkTap() = runComposeUiTest {
        var clickedUrl: String? = null
        val html = "<p>Highlighted<a href=\"https://example.com/term\">LinkTerm</a></p>"
        val highlight = TextHighlight(
            id = 10L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "p:0",
            startOffset = 0,
            endOffset = "Highlighted".length,
            highlightedText = "Highlighted",
            color = HighlightColor.YELLOW,
            createdAt = 1L
        )

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(400.dp, 400.dp).padding(top = 120.dp)) {
                    RichText(
                        html = html,
                        onLinkClick = { clickedUrl = it },
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = listOf(highlight),
                            onHighlightAdd = { _, _, _, _, _, _ -> },
                            onHighlightRemove = {},
                            onHighlightColorChange = { _, _ -> },
                        ),
                    )
                }
            }
        }

        val textNode = onNodeWithText("HighlightedLinkTerm").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()

        // Tap the very first character of "LinkTerm" (immediately adjacent to highlight endOffset)
        val firstLinkCharBox = layout.getBoundingBox("Highlighted".length)
        textNode.performTouchInput {
            click(Offset(firstLinkCharBox.left + 1f, (firstLinkCharBox.top + firstLinkCharBox.bottom) / 2f))
        }
        waitForIdle()

        assertEquals("https://example.com/term", clickedUrl)
        onNodeWithContentDescription("Delete highlight").assertDoesNotExist()
    }

    @Test
    fun tappingSecondHighlightInSameParagraphKeepsSecondToolbarOpen() = runComposeUiTest {
        val textStr = "FirstHighlight plain SecondHighlight"
        val h1 = TextHighlight(
            id = 1L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "p:0",
            startOffset = 0,
            endOffset = "FirstHighlight".length,
            highlightedText = "FirstHighlight",
            color = HighlightColor.YELLOW,
            createdAt = 1L
        )
        val h2Start = textStr.indexOf("SecondHighlight")
        val h2 = TextHighlight(
            id = 2L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "p:0",
            startOffset = h2Start,
            endOffset = textStr.length,
            highlightedText = "SecondHighlight",
            color = HighlightColor.PINK,
            createdAt = 2L
        )

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(500.dp, 400.dp).padding(top = 120.dp)) {
                    RichText(
                        html = "<p>$textStr</p>",
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = listOf(h1, h2),
                            onHighlightAdd = { _, _, _, _, _, _ -> },
                            onHighlightRemove = {},
                            onHighlightColorChange = { _, _ -> },
                        ),
                    )
                }
            }
        }

        val textNode = onNodeWithText(textStr).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()

        // Tap Highlight 1 (Yellow)
        val h1Box = layout.getBoundingBox(2)
        textNode.performTouchInput {
            click(Offset(h1Box.left + 2f, (h1Box.top + h1Box.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithContentDescription("Yellow highlight, selected, tap to remove").assertIsDisplayed()

        // Tap Highlight 2 (Pink) in the SAME paragraph: must switch to Pink, NOT close itself
        val h2Box = layout.getBoundingBox(h2Start + 2)
        textNode.performTouchInput {
            click(Offset(h2Box.left + 2f, (h2Box.top + h2Box.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithContentDescription("Pink highlight, selected, tap to remove").assertIsDisplayed()
        onNodeWithContentDescription("Delete highlight").assertIsDisplayed()
    }

    @Test
    fun dismissingMidHandleDragDoesNotResurrectSelection() = runComposeUiTest {
        lateinit var hostState: RichTextHighlightHostState
        setContent {
            AppTheme {
                hostState = rememberRichTextHighlightHostState()
                RichTextHighlightHost(state = hostState) {
                    Box(modifier = Modifier.requiredSize(400.dp, 400.dp).padding(top = 120.dp)) {
                        RichText(
                            html = "<p>Acute hyponatremia causes confusion</p>",
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
            }
        }

        val textNode = onNodeWithText("Acute hyponatremia causes confusion").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val wordBox = layouts.first().getBoundingBox(8)

        textNode.performTouchInput {
            longClick(Offset(wordBox.left + 2f, (wordBox.top + wordBox.bottom) / 2f))
        }
        waitForIdle()

        val endHandle = onNodeWithTag("selection_handle_end").assertIsDisplayed()
        onNodeWithText("Copy").assertIsDisplayed()

        // Start dragging end handle, then dismiss mid-drag -> must NOT resurrect selection
        endHandle.performTouchInput {
            down(center)
            moveBy(Offset(20f, 0f))
        }
        waitForIdle()
        onNodeWithText("Copy").assertDoesNotExist()

        runOnIdle { hostState.dismiss() }
        waitForIdle()
        onNodeWithText("Copy").assertDoesNotExist()
        onNodeWithTag("selection_handle_start").assertDoesNotExist()
    }

    @Test
    fun scrollAndOuterTapDismissToolbarAndHandles() = runComposeUiTest {
        setContent {
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                val scrollState = rememberScrollState()
                LaunchedEffect(scrollState.isScrollInProgress) {
                    if (scrollState.isScrollInProgress) hostState.dismiss()
                }
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(400.dp, 400.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(top = 120.dp, bottom = 300.dp)
                    ) {
                        RichText(
                            html = "<p>Acute hyponatremia causes confusion</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = emptyList(),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("outer_tap_area")
                        )
                    }
                }
            }
        }

        val textNode = onNodeWithText("Acute hyponatremia causes confusion").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val wordBox = layouts.first().getBoundingBox(8)

        // 1. Select word, then tap outer sibling area -> dismisses toolbar and handles
        textNode.performTouchInput {
            longClick(Offset(wordBox.left + 2f, (wordBox.top + wordBox.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithText("Copy").assertIsDisplayed()
        onNodeWithTag("selection_handle_start").assertIsDisplayed()

        onNodeWithTag("outer_tap_area").performTouchInput { click() }
        waitForIdle()
        onNodeWithText("Copy").assertDoesNotExist()
        onNodeWithTag("selection_handle_start").assertDoesNotExist()

        // 2. Select again, then scroll -> dismisses both toolbar and handles
        textNode.performTouchInput {
            longClick(Offset(wordBox.left + 2f, (wordBox.top + wordBox.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithText("Copy").assertIsDisplayed()
        onNodeWithTag("selection_handle_start").assertIsDisplayed()

        textNode.performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        waitForIdle()
        onNodeWithText("Copy").assertDoesNotExist()
        onNodeWithTag("selection_handle_start").assertDoesNotExist()
    }

    @Test
    fun headingAndCodeBlockSupportPerBlockHighlighting() = runComposeUiTest {
        val recordedAdds = mutableListOf<Pair<String, String>>()
        val html = """
            <h3>Diagnostic Criteria</h3>
            <pre><code>sodium &lt; 135 mEq/L</code></pre>
        """.trimIndent()

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(500.dp, 500.dp).padding(top = 140.dp)) {
                    RichText(
                        html = html,
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = emptyList(),
                            onHighlightAdd = { _, blockKey, _, _, text, _ ->
                                recordedAdds += blockKey to text
                            },
                            onHighlightRemove = {},
                            onHighlightColorChange = { _, _ -> },
                        ),
                    )
                }
            }
        }

        val headingNode = onNodeWithText("Diagnostic Criteria").assertIsDisplayed()
        headingNode.performTouchInput { longClick(centerLeft + Offset(12f, 0f)) }
        waitForIdle()
        onNodeWithContentDescription("Yellow highlight").performClick()
        waitForIdle()

        val codeNode = onNodeWithText("sodium < 135 mEq/L").assertIsDisplayed()
        codeNode.performTouchInput { longClick(centerLeft + Offset(12f, 0f)) }
        waitForIdle()
        onNodeWithContentDescription("Green highlight").performClick()
        waitForIdle()

        assertEquals(2, recordedAdds.size)
        assertEquals("h:0" to "Diagnostic", recordedAdds[0])
        assertEquals("code:1" to "sodium", recordedAdds[1])
    }

    @Test
    fun shiftedLocalOffsetHighlightReanchorsAndOpensEditToolbar() = runComposeUiTest {
        var deletedId: Long? = null
        val textStr = "Updated serum sodium level"
        // Stored offsets 0..5 pointed elsewhere after parser change, while quote is "sodium" at 14..20
        val shifted = TextHighlight(
            id = 42L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "p:0",
            startOffset = 0,
            endOffset = 6,
            highlightedText = "sodium",
            color = HighlightColor.BLUE,
            createdAt = 1L,
        )

        setContent {
            AppTheme {
                Box(modifier = Modifier.requiredSize(500.dp, 400.dp).padding(top = 120.dp)) {
                    RichText(
                        html = "<p>$textStr</p>",
                        highlightConfig = RichTextHighlightConfig(
                            section = HighlightSection.QUESTION,
                            highlights = listOf(shifted),
                            onHighlightAdd = { _, _, _, _, _, _ -> },
                            onHighlightRemove = { deletedId = it },
                            onHighlightColorChange = { _, _ -> },
                        ),
                    )
                }
            }
        }

        val textNode = onNodeWithText(textStr).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val reanchoredCharBox = layouts.first().getBoundingBox(textStr.indexOf("sodium") + 2)

        textNode.performTouchInput {
            click(Offset(reanchoredCharBox.left + 1f, (reanchoredCharBox.top + reanchoredCharBox.bottom) / 2f))
        }
        waitForIdle()

        onNodeWithContentDescription("Delete highlight").performClick()
        waitForIdle()
        assertEquals(42L, deletedId)
    }

    @Test
    fun tappingHighlightTwiceThenAnotherParagraphNeverDrawsTwoToolbarsAndDismissesOutside() = runComposeUiTest {
        val h1 = TextHighlight(
            id = 1L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:0", startOffset = 0, endOffset = 5,
            highlightedText = "Alpha", color = HighlightColor.YELLOW, createdAt = 1L,
        )
        val h2 = TextHighlight(
            id = 2L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:1", startOffset = 0, endOffset = 5,
            highlightedText = "Gamma", color = HighlightColor.BLUE, createdAt = 2L,
        )

        setContent {
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(500.dp, 600.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 180.dp)) {
                        RichText(
                            html = "<p>Alpha beta</p><p>Gamma delta</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = listOf(h1, h2),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).testTag("outside_area"))
                    }
                }
            }
        }

        val p1 = onNodeWithText("Alpha beta").assertIsDisplayed()
        val p1Layouts = mutableListOf<TextLayoutResult>()
        p1.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p1Layouts)
        val h1Box = p1Layouts.first().getBoundingBox(2)

        val p2 = onNodeWithText("Gamma delta").assertIsDisplayed()
        val p2Layouts = mutableListOf<TextLayoutResult>()
        p2.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p2Layouts)
        val h2Box = p2Layouts.first().getBoundingBox(2)

        // Tap highlight 1 twice
        p1.performTouchInput { click(Offset(h1Box.left + 2f, (h1Box.top + h1Box.bottom) / 2f)) }
        waitForIdle()
        p1.performTouchInput { click(Offset(h1Box.left + 2f, (h1Box.top + h1Box.bottom) / 2f)) }
        waitForIdle()

        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(1)

        // Tap highlight 2 in second paragraph -> must replace toolbar, never show 2
        p2.performTouchInput { click(Offset(h2Box.left + 2f, (h2Box.top + h2Box.bottom) / 2f)) }
        waitForIdle()
        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(1)
        onNodeWithContentDescription("Blue highlight, selected, tap to remove").assertIsDisplayed()

        // Tapping outside dismisses the single toolbar cleanly
        onNodeWithTag("outside_area").performTouchInput { click() }
        waitForIdle()
        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(0)
    }

    @Test
    fun selectingTextThenTappingHighlightDismissesOutsideAndNeverAccumulatesToolbars() = runComposeUiTest {
        val h1 = TextHighlight(
            id = 1L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:0", startOffset = 0, endOffset = 5,
            highlightedText = "Alpha", color = HighlightColor.YELLOW, createdAt = 1L,
        )
        val h2 = TextHighlight(
            id = 2L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:1", startOffset = 0, endOffset = 5,
            highlightedText = "Gamma", color = HighlightColor.PINK, createdAt = 2L,
        )

        setContent {
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(500.dp, 600.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 180.dp)) {
                        RichText(
                            html = "<p>Alpha plainword</p><p>Gamma delta</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = listOf(h1, h2),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).testTag("outside_area"))
                    }
                }
            }
        }

        val p1 = onNodeWithText("Alpha plainword").assertIsDisplayed()
        val p1Layouts = mutableListOf<TextLayoutResult>()
        p1.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p1Layouts)
        val plainBox = p1Layouts.first().getBoundingBox(8)
        val h1Box = p1Layouts.first().getBoundingBox(2)

        val p2 = onNodeWithText("Gamma delta").assertIsDisplayed()
        val p2Layouts = mutableListOf<TextLayoutResult>()
        p2.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p2Layouts)
        val h2Box = p2Layouts.first().getBoundingBox(2)

        // 1. Long-press to select text -> Create toolbar appears
        p1.performTouchInput { longClick(Offset(plainBox.left + 2f, (plainBox.top + plainBox.bottom) / 2f)) }
        waitForIdle()
        onNodeWithText("Copy").assertIsDisplayed()

        // 2. Tap highlighted text in same paragraph -> switches to Edit toolbar (no handles, still 1 toolbar)
        p1.performTouchInput { click(Offset(h1Box.left + 2f, (h1Box.top + h1Box.bottom) / 2f)) }
        waitForIdle()
        onNodeWithTag("selection_handle_start").assertDoesNotExist()
        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(1)

        // 3. Tap another highlight in paragraph 2 -> replaces toolbar, still only 1 toolbar
        p2.performTouchInput { click(Offset(h2Box.left + 2f, (h2Box.top + h2Box.bottom) / 2f)) }
        waitForIdle()
        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(1)
        onNodeWithContentDescription("Pink highlight, selected, tap to remove").assertIsDisplayed()

        // 4. Tap outside -> dismisses completely
        onNodeWithTag("outside_area").performTouchInput { click() }
        waitForIdle()
        onAllNodesWithContentDescription("Delete highlight").assertCountEquals(0)
        onNodeWithText("Copy").assertDoesNotExist()
    }

    @Test
    fun toolbarRepositionsNearParagraph2WhenSwitchingFromParagraph1() = runComposeUiTest {
        val h1 = TextHighlight(
            id = 1L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:0", startOffset = 0, endOffset = 5,
            highlightedText = "Alpha", color = HighlightColor.YELLOW, createdAt = 1L,
        )
        val h2 = TextHighlight(
            id = 2L, dbName = "db", questionId = 1L, section = HighlightSection.EXPLANATION,
            blockKey = "p:0", startOffset = 0, endOffset = 5,
            highlightedText = "Gamma", color = HighlightColor.BLUE, createdAt = 2L,
        )

        setContent {
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(500.dp, 700.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 180.dp)) {
                        RichText(
                            html = "<p>Alpha beta</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = listOf(h1),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                        Spacer(modifier = Modifier.height(160.dp))
                        RichText(
                            html = "<p>Gamma delta</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.EXPLANATION,
                                highlights = listOf(h2),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                    }
                }
            }
        }

        val p1 = onNodeWithText("Alpha beta").assertIsDisplayed()
        val p1Layouts = mutableListOf<TextLayoutResult>()
        p1.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p1Layouts)
        val h1Box = p1Layouts.first().getBoundingBox(2)
        val p1WindowBounds = p1.fetchSemanticsNode().boundsInWindow

        val p2 = onNodeWithText("Gamma delta").assertIsDisplayed()
        val p2Layouts = mutableListOf<TextLayoutResult>()
        p2.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p2Layouts)
        val h2Box = p2Layouts.first().getBoundingBox(2)
        val p2WindowBounds = p2.fetchSemanticsNode().boundsInWindow

        // Tap highlight in Paragraph 1 -> toolbar sits above Paragraph 1
        p1.performTouchInput { click(Offset(h1Box.left + 2f, (h1Box.top + h1Box.bottom) / 2f)) }
        waitForIdle()
        val toolbar1Bounds = onNodeWithContentDescription("Delete highlight").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(toolbar1Bounds.bottom <= p1WindowBounds.top + 1f, "Expected toolbar1 above p1: $toolbar1Bounds vs $p1WindowBounds")

        // Tap highlight in Paragraph 2 -> toolbar moves below Paragraph 1 and sits above Paragraph 2
        p2.performTouchInput { click(Offset(h2Box.left + 2f, (h2Box.top + h2Box.bottom) / 2f)) }
        waitForIdle()
        val toolbar2Bounds = onNodeWithContentDescription("Delete highlight").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(toolbar2Bounds.top > p1WindowBounds.bottom, "Expected toolbar2 below p1: $toolbar2Bounds vs $p1WindowBounds")
        assertTrue(toolbar2Bounds.bottom <= p2WindowBounds.top + 1f, "Expected toolbar2 above p2: $toolbar2Bounds vs $p2WindowBounds")
    }

    @Test
    fun scrolledContainerPlacesToolbarNearVisibleParagraphBounds() = runComposeUiTest {
        val h = TextHighlight(
            id = 1L, dbName = "db", questionId = 1L, section = HighlightSection.QUESTION,
            blockKey = "p:0", startOffset = 0, endOffset = 6,
            highlightedText = "Target", color = HighlightColor.GREEN, createdAt = 1L,
        )
        var viewportHeightPx = 0f

        setContent {
            viewportHeightPx = with(LocalDensity.current) { 420.dp.toPx() }
            val scrollTargetPx = with(LocalDensity.current) { 340.dp.roundToPx() }
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                val scrollState = rememberScrollState()
                LaunchedEffect(scrollTargetPx) {
                    scrollState.scrollTo(scrollTargetPx)
                }
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(500.dp, 420.dp),
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Spacer(modifier = Modifier.height(500.dp))
                        RichText(
                            html = "<p>Target highlight text</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = listOf(h),
                                onHighlightAdd = { _, _, _, _, _, _ -> },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                        Spacer(modifier = Modifier.height(300.dp))
                    }
                }
            }
        }

        waitForIdle()
        val targetNode = onNodeWithText("Target highlight text").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        targetNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val hBox = layouts.first().getBoundingBox(2)
        val targetWindowBounds = targetNode.fetchSemanticsNode().boundsInWindow

        targetNode.performTouchInput { click(Offset(hBox.left + 2f, (hBox.top + hBox.bottom) / 2f)) }
        waitForIdle()

        val toolbarBounds = onNodeWithContentDescription("Delete highlight").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(toolbarBounds.top >= 0f && toolbarBounds.bottom <= viewportHeightPx, "Toolbar outside viewport: $toolbarBounds")
        assertTrue(toolbarBounds.bottom <= targetWindowBounds.top + 1f, "Expected toolbar above scrolled target: $toolbarBounds vs $targetWindowBounds")
    }

    @Test
    fun handleDragWorksInNonFirstParagraphWithIdenticalText() = runComposeUiTest {
        var recordedAdd: Pair<String, String>? = null
        val sentence = "Acute hyponatremia causes confusion"

        setContent {
            AppTheme {
                val hostState = rememberRichTextHighlightHostState()
                RichTextHighlightHost(
                    state = hostState,
                    modifier = Modifier.requiredSize(500.dp, 650.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 180.dp, start = 24.dp, end = 24.dp)) {
                        RichText(
                            html = "<p>$sentence</p><p>$sentence</p>",
                            highlightConfig = RichTextHighlightConfig(
                                section = HighlightSection.QUESTION,
                                highlights = emptyList(),
                                onHighlightAdd = { _, blockKey, _, _, selectedText, _ ->
                                    recordedAdd = blockKey to selectedText
                                },
                                onHighlightRemove = {},
                                onHighlightColorChange = { _, _ -> },
                            ),
                        )
                    }
                }
            }
        }

        val p1 = onAllNodesWithText(sentence)[0].assertIsDisplayed()
        val p1Layouts = mutableListOf<TextLayoutResult>()
        p1.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p1Layouts)
        val p1WordBox = p1Layouts.first().getBoundingBox(8)

        val p2 = onAllNodesWithText(sentence)[1].assertIsDisplayed()
        val p2Layouts = mutableListOf<TextLayoutResult>()
        p2.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(p2Layouts)
        val p2Layout = p2Layouts.first()
        val p2WordBox = p2Layout.getBoundingBox(8)
        val p2WindowBounds = p2.fetchSemanticsNode().boundsInWindow

        // 1. Long-press paragraph 1 first so drag handle pointerInput is initialized on the identical text
        p1.performTouchInput {
            longClick(Offset(p1WordBox.left + 2f, (p1WordBox.top + p1WordBox.bottom) / 2f))
        }
        waitForIdle()
        onNodeWithTag("selection_handle_start").assertIsDisplayed()

        // 2. Long-press paragraph 2 (identical text) and verify handles reposition onto paragraph 2
        p2.performTouchInput {
            longClick(Offset(p2WordBox.left + 2f, (p2WordBox.top + p2WordBox.bottom) / 2f))
        }
        waitForIdle()
        val startHandleBounds = onNodeWithTag("selection_handle_start").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(startHandleBounds.top >= p2WindowBounds.top, "Expected start handle on p2: $startHandleBounds vs $p2WindowBounds")

        // 3. Drag paragraph 2's end handle rightward across " causes" and create highlight
        val endHandle = onNodeWithTag("selection_handle_end").assertIsDisplayed()
        val endTargetBox = p2Layout.getBoundingBox(24) // inside "causes"
        val currentEndRight = p2Layout.getBoundingBox(17).right
        val deltaRight = (endTargetBox.right - currentEndRight).coerceAtLeast(40f)
        endHandle.performTouchInput {
            down(center)
            moveBy(Offset(deltaRight, 0f))
            up()
        }
        waitForIdle()

        onNodeWithContentDescription("Yellow highlight").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals("p:1", recordedAdd?.first)
        val capturedText = recordedAdd?.second
        assertTrue(
            capturedText != null && capturedText.contains("hyponatremia") && capturedText.contains("causes"),
            "Expected expanded selection in p:1 containing 'hyponatremia' and 'causes', got '$capturedText'"
        )
    }
}
