package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.ui.richtext.parser.CssParser
import com.medqb.app.shared.ui.richtext.parser.InlineHighlight
import com.medqb.app.shared.ui.richtext.parser.InlineStyle
import com.medqb.app.shared.ui.richtext.parser.RichTextParser
import com.medqb.app.shared.ui.richtext.parser.applyClassStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextStreamliningTest {

    private val dummyPalette = RichTextPalette(
        importantBackground = Color.Red,
        importantText = Color.Black,
        selectedBackground = Color.Blue,
        selectedText = Color.White,
        linkText = Color.Cyan,
        dictionaryText = Color.Green,
        abstractText = Color.Gray
    )

    @Test
    fun testCssParserTextAlign() {
        assertEquals(TextAlign.Center, CssParser.parseTextAlign("center", ""))
        assertEquals(TextAlign.End, CssParser.parseTextAlign("right", ""))
        assertEquals(TextAlign.Start, CssParser.parseTextAlign("left", ""))
        assertEquals(TextAlign.Justify, CssParser.parseTextAlign("justify", ""))
        assertNull(CssParser.parseTextAlign("unknown", ""))

        // CSS style fallback
        assertEquals(TextAlign.Center, CssParser.parseTextAlign("", "text-align: center; color: red;"))
        assertEquals(TextAlign.End, CssParser.parseTextAlign("", "font-size: 14px; text-align: right"))
        assertEquals(TextAlign.Justify, CssParser.parseTextAlign("", "text-align: justify !important;"))
        assertNull(CssParser.parseTextAlign("", "color: blue; width: 100px;"))
    }

    @Test
    fun testHighlightColorToComposeColor() {
        assertEquals(Color(0xFFFFEB3B), HighlightColor.YELLOW.toComposeColor())
        assertEquals(Color(0xFF4CAF50), HighlightColor.GREEN.toComposeColor())
        assertEquals(Color(0xFF2196F3), HighlightColor.BLUE.toComposeColor())
        assertEquals(Color(0xFFE91E63), HighlightColor.PINK.toComposeColor())
        assertEquals(Color(0xFFFF9800), HighlightColor.ORANGE.toComposeColor())
    }

    @Test
    fun testInlineStyleApplyClassStyles() {
        val base = InlineStyle()

        val important = base.applyClassStyles(setOf("important"), dummyPalette, showSelectedHighlight = true)
        assertEquals(InlineHighlight.IMPORTANT, important.highlight)
        assertTrue(important.bold)

        val selectedWithFlag = base.applyClassStyles(setOf("selected"), dummyPalette, showSelectedHighlight = true)
        assertEquals(InlineHighlight.SELECTED, selectedWithFlag.highlight)

        val selectedWithoutFlag = base.applyClassStyles(setOf("selected"), dummyPalette, showSelectedHighlight = false)
        assertNull(selectedWithoutFlag.highlight)

        val dictionary = base.applyClassStyles(setOf("dictionary"), dummyPalette, showSelectedHighlight = true)
        assertTrue(dictionary.dictionary)
        assertTrue(dictionary.underline)

        val abstractStyle = base.applyClassStyles(setOf("abstract"), dummyPalette, showSelectedHighlight = true)
        assertTrue(abstractStyle.smallText)
        assertEquals(dummyPalette.abstractText, abstractStyle.textColor)

        val metaLink = base.applyClassStyles(setOf("metalink"), dummyPalette, showSelectedHighlight = true)
        assertTrue(metaLink.italic)
        assertEquals(dummyPalette.linkText, metaLink.textColor)
    }

    @Test
    fun testParseListBlocks() {
        val html = """
            <ul>
                <li>First item</li>
                <li>Second item</li>
            </ul>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, false)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichTextBlock.BulletList)
        val bulletList = blocks[0] as RichTextBlock.BulletList
        assertEquals(2, bulletList.items.size)
        assertEquals("First item", bulletList.items[0].text)
        assertEquals("Second item", bulletList.items[1].text)
    }

    @Test
    fun testParseParagraphWithFormattedText() {
        val html = "<p><strong>Bold</strong> and <em>italic</em> and <span class=\"wichtig\">important</span></p>"
        val blocks = RichTextParser.parse(html, dummyPalette, true)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichTextBlock.Paragraph)
        val para = blocks[0] as RichTextBlock.Paragraph
        assertEquals("Bold and italic and important", para.text.text)
    }
}
