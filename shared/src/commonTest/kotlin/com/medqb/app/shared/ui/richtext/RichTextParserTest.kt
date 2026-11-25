package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.medqb.app.shared.ui.richtext.parser.CssParser
import com.medqb.app.shared.ui.richtext.parser.HtmlEntities
import com.medqb.app.shared.ui.richtext.parser.InlineHighlight
import com.medqb.app.shared.ui.richtext.parser.InlineStyle
import com.medqb.app.shared.ui.richtext.parser.RichTextParser
import com.medqb.app.shared.ui.richtext.parser.applyClassStyles
import com.medqb.app.shared.utils.HtmlUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextParserTest {

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

    @Test
    fun tooltipAttributesPreserveVisibleWordAndInequalities() {
        val html = """
            <p>
                <span data-tooltip-text="Serum pH &lt;7.35 and &gt;7.20 indicates mild acidemia">Acidemia</span>
                and
                <span data-tooltip-content="Na &lt;135 mEq/L">hyponatremia</span>
            </p>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, showSelectedHighlight = false)
        assertEquals(1, blocks.size)
        val para = blocks.first() as RichTextBlock.Paragraph
        assertTrue(para.text.text.contains("Acidemia"))
        assertTrue(para.text.text.contains("hyponatremia"))

        val tooltips = para.text.getStringAnnotations("TOOLTIP", 0, para.text.length)
        assertEquals(2, tooltips.size)
        assertEquals("Serum pH <7.35 and >7.20 indicates mild acidemia", tooltips[0].item)
        assertEquals("Na <135 mEq/L", tooltips[1].item)
    }

    @Test
    fun nestedInlineTooltipDoesNotBleedIntoOuterBoldSpan() {
        val html = """
            <p><span class="wichtig">Bold prefix <span class="dictionary">target<span class="tooltip-text">Definition</span></span> suffix</span></p>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, showSelectedHighlight = false)
        val para = blocks.first() as RichTextBlock.Paragraph
        assertEquals("Bold prefix target suffix", para.text.text)

        val tooltips = para.text.getStringAnnotations("TOOLTIP", 0, para.text.length)
        assertEquals(1, tooltips.size)
        assertEquals("target", para.text.text.substring(tooltips.first().start, tooltips.first().end))
    }

    @Test
    fun htmlEntitiesDecodeAlphanumericGreekMathAndRejectSurrogates() {
        assertEquals("½ ¼ ¾ ¹ ² ³", HtmlEntities.decode("&frac12; &frac14; &frac34; &sup1; &sup2; &sup3;"))
        assertEquals("α β γ δ μ ≤ ≥ ≠ ≈ ≈ − • · ↔", HtmlEntities.decode("&alpha; &beta; &gamma; &delta; &mu; &le; &ge; &ne; &approx; &asymp; &minus; &bull; &middot; &harr;"))
        assertEquals("A", HtmlEntities.decode("&#X41;"))
        assertEquals("&#AB;", HtmlEntities.decode("&#AB;"))
        assertEquals("&#xD800;", HtmlEntities.decode("&#xD800;"))
        assertEquals("&#55296;", HtmlEntities.decode("&#55296;"))
    }

    @Test
    fun inlineCodeInsideDivStaysInlineAndUnbalancedCloseTagDoesNotLeakBold() {
        val html = """
            <div>Gene <code>BRCA1</code> mutation</div>
            <p><span class="wichtig"><b>Assessing <span>serum osmolality</a></b></span> plain rest</p>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, showSelectedHighlight = false)
        assertEquals(2, blocks.size)
        val codePara = blocks[0] as RichTextBlock.Paragraph
        assertEquals("Gene BRCA1 mutation", codePara.text.text)

        val unbalancedPara = blocks[1] as RichTextBlock.Paragraph
        assertEquals("Assessing serum osmolality plain rest", unbalancedPara.text.text)
        val plainStart = unbalancedPara.text.text.indexOf("plain rest")
        val boldCoveringPlain = unbalancedPara.text.spanStyles.any { span ->
            span.item.fontWeight == FontWeight.SemiBold && span.end > plainStart
        }
        assertTrue(!boldCoveringPlain, "Bold style should not leak past </b></span> onto 'plain rest'")
    }

    @Test
    fun nestedListsIndentProperlyAndMediaInListIsPreserved() {
        val html = """
            <ul>
                <li>Outer
                    <ul>
                        <li>Inner</li>
                    </ul>
                </li>
                <li>With image <img src="/media/diagram.png" alt="Diagram" /></li>
            </ul>
            <ul>
                <li>Direct A</li>
                <ul>
                    <li>Direct B</li>
                </ul>
            </ul>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, showSelectedHighlight = false)
        val lists = blocks.filterIsInstance<RichTextBlock.BulletList>()
        assertEquals(2, lists.size)
        val list = lists[0]
        assertEquals(2, list.items.size)
        assertTrue(list.items[0].text.startsWith("Outer"), "Got: ${list.items[0].text}")
        assertTrue(list.items[0].text.contains("\n  ◦ Inner"), "Got: ${list.items[0].text}")
        val media = blocks.filterIsInstance<RichTextBlock.Media>().singleOrNull()
        assertNotNull(media)
        assertEquals("diagram.png", media.mediaRef)

        val directNestedList = lists[1]
        assertEquals(1, directNestedList.items.size)
        assertTrue(directNestedList.items[0].text.contains("Direct A\n  ◦ Direct B"), "Got: ${directNestedList.items[0].text}")
    }

    @Test
    fun testAmbossExplanationParsing() {
        val snippet = """
<div><h4>Correct Answer Is D [ 61% ]</h4><p>
<span class="wichtig"><b>Assessing <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a></b></span> is the first step in evaluating confirmed <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a> to differentiate between <span class="wichtig"><b>hypotonic</b></span> (low <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>), <span class="wichtig"><b>hypertonic</b></span> (high <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>), and <span class="wichtig"><b>isotonic</b></span> (normal <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>) <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">Hypotonic hyponatremia</a> is the most common type of <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. Further diagnostic evaluation of <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> includes assessing <span data-learningcard-id="kg0mv2" data-anker="Ze5c2933e2f4ca7e9f716818005ff069a" data-section-id="jJX_F_" data-sememe-id="Xqc9CW0">urine osmolality</a> (to determine <span data-learningcard-id="AT0Rt2" data-anker="Zbf62754ce74881b6657f0771bc811fd7" data-section-id="XK19U30" data-sememe-id="tKaXRl">antidiuretic hormone</a> activity), determining <span data-learningcard-id="fM0kLg" data-anker="Z67f90fc721c4b76620b3107bc207b164" data-section-id="891O6Q0">volume status</span> (to assess whether <span data-learningcard-id="AT0Rt2" data-anker="Zbf62754ce74881b6657f0771bc811fd7" data-section-id="XK19U30" data-sememe-id="tKaXRl">antidiuretic hormone</a> activity is appropriate), and assessing urinary <span data-sememe-id="arWQf50">sodium</span> and/or <span data-learningcard-id="kg0mv2" data-anker="Zd235f5f8bd029e49126829f0e80c2831" data-section-id="PJXW8_" data-sememe-id="Os0IEh">fractional excretion of sodium</a> (to determine if the cause is renal or extrarenal).
<br><br>This patient likely has <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> caused by <span data-learningcard-id="zT0rt2" data-anker="Z849dca7797cad193a13bd0c4d86128bc" data-sememe-id="uP0pgT">SIADH</span> following <span data-learningcard-id="_N05dg" data-anker="Zb1bb43280a4d0d86f2d53818b9e8afc8" data-section-id="LBXwY00" data-sememe-id="wQahy4">SSRI</span> initiation.</p><p></p></div><div style="align-items:center; text-align: center;"><img src="big_6826eb649041f5.12084029.jpg" width="200px" style="padding:20px;"></div>
<div><h4>[ A ] [ 1% ]</h4><p>
<span data-learningcard-id="gm0FUg" data-anker="Zc79a41be3ea73012acd816d5e2bf130d" data-section-id="WkcP5c0" data-sememe-id="t4aXOk">Furosemide</a> may be considered for the management of <span data-learningcard-id="rg0f92" data-anker="Z854654a69f8b6617bc9d64f46fe44b5a" data-section-id="8G0Oa3" data-sememe-id="4h13Vg0">hypervolemic hyponatremia</a>. This patient does not have symptoms of <span data-sememe-id="Dk11JS0">hypervolemia</span> (e.g., <span data-learningcard-id="KS0U0f" data-anker="Z48e76ffce1b364c1ba4dacdd79a56b7b" data-sememe-id="_405NT">ascites</a>, <span data-learningcard-id="SM0yLg" data-anker="Zc00dca4994157e86d8e6e8ee9510443f" data-section-id="JcXsWC" data-sememe-id="Oo0IXS">edema</a>) or features of conditions that cause <span data-learningcard-id="rg0f92" data-anker="Z854654a69f8b6617bc9d64f46fe44b5a" data-section-id="8G0Oa3" data-sememe-id="4h13Vg0">hypervolemic hyponatremia</a> like <span data-learningcard-id="rS0faf" data-anker="Zfdf718ee7030d25f9dfb6b6603941bd5" data-section-id="Slcywc0" data-sememe-id="pwdLPH0">congestive heart failure</a>, <span data-learningcard-id="PS0W-2" data-anker="Za99be06553ff299396a513bb65ed71be" data-sememe-id="D401lT">cirrhosis</a>, or <span data-learningcard-id="lg0vv2" data-anker="Zbdc3b2167c4bab75e0f1bf58faab9e22" data-section-id="l70vNh" data-sememe-id="qQ1CCg0">renal failure</span> with low urine output. <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">Hyponatremia</a> in this patient likely resulted from <span data-learningcard-id="zT0rt2" data-anker="Z849dca7797cad193a13bd0c4d86128bc" data-sememe-id="uP0pgT">SIADH</span> (triggered by <span data-learningcard-id="_N05dg" data-anker="Zb1bb43280a4d0d86f2d53818b9e8afc8" data-section-id="LBXwY00" data-sememe-id="wQahy4">SSRI</a> initiation), which typically manifests with <span data-learningcard-id="rg0f92" data-anker="Z83d9522134c8caaf6b03c6fa1566400a" data-section-id="8G0Oa3" data-sememe-id="Ph1WVg0">euvolemic hyponatremia</a>. Additional diagnostics are required to inform further management.</p><p></p></div>
<div><h4>[ B ] [ 23% ]</h4><p>
<span data-sememe-id="wk1hJS0">Hypertonic saline</span> is indicated in <span data-learningcard-id="rg0f92" data-anker="Zcd01c7bb7bc2292e790597f0e9da6d81" data-section-id="vG0Aa3" data-sememe-id="BM1zIh0">severely symptomatic hyponatremia</span> to increase serum <span data-sememe-id="arWQf50">sodium</span> rapidly to prevent <span data-learningcard-id="HL0K_g" data-anker="Zaca1b98782f4f2631c06f0414a59ee7b" data-section-id="-gcDBb0" data-sememe-id="a-0QDi">cerebral edema</span> and <span data-learningcard-id="HL0K_g" data-anker="Z7ca422322bb96d80a2b4054adfb6b1ff" data-section-id="sgct9b0" data-sememe-id="hSbczG">brain herniation</a>. Rapid <span data-sememe-id="arWQf50">sodium</span> correction is unnecessary and potentially harmful in patients with nonsevere symptoms due to the risk of overcorrection and subsequent <span data-learningcard-id="rg0f92" data-anker="Zf9d046b260590d60860353fc1977a8aa" data-section-id="cU1aXT0" data-sememe-id="ksYmEq">osmotic demyelination syndrome</span>. <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-section-id="n_Y7K7">Hyponatremia</a> with nonsevere symptoms (usually <span data-learningcard-id="rg0f92" data-anker="Z4cf45c6982fded7aca34453024148d43" data-section-id="tG0Xa3" data-sememe-id="uM1pqh0">chronic hyponatremia</span>) requires a slow <span data-sememe-id="arWQf50">sodium</span> correction rate.</p><p></p></div>
<div><h4>[ C ] [ 13% ]</h4><p>The <span data-learningcard-id="kg0mv2" data-anker="Zd235f5f8bd029e49126829f0e80c2831" data-section-id="PJXW8_" data-sememe-id="Os0IEh">fractional excretion of sodium</span> should be determined in patients with <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> to assess whether the cause is renal or extrarenal. However, first, it is important to establish whether the patient has hypotonic, isotonic, or <span data-learningcard-id="rg0f92" data-anker="Zcb41877d9f5bbc450e14fef0a51d0027" data-section-id="8G0Oa3" data-sememe-id="KrcURd0">hypertonic hyponatremia</a>.</p><p></p></div>
<div><h4>[ E ] [ 2% ]</h4><p>Measuring serum <span data-learningcard-id="AT0Rt2" data-anker="Zbc03040ec893396d15c13d0b69e3dba4" data-section-id="XK19U30" data-sememe-id="jKa_Tl">TSH</span> and <span data-learningcard-id="V60GPS" data-anker="Z68437810c311fa1e0e910fecfa30be04" data-section-id="BtczfV0" data-sememe-id="-KaDQl">cortisol</span> levels may be appropriate later in the workup as both <span data-learningcard-id="cg0a82" data-anker="Z3fd41eef8b8ce3cec9c7eda727b6d1ce" data-section-id="HJ0KvS" data-sememe-id="CP0qST">hypothyroidism</span> and <span data-learningcard-id="Ug0bu2" data-anker="Z351bdb8ac6670de066f05e6a1a8a74a2" data-section-id="HJ0KvS" data-sememe-id="TlX6wy">hypocortisolism</span> can manifest with <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. A different diagnostic study is more appropriate at this stage.</p><p></p></div>
"""
        val sanitized = HtmlUtils.sanitizeForRichText(snippet)
        val blocks = RichTextParser.parse(sanitized, dummyPalette, true)
        assertEquals(11, blocks.size)

        val heading0 = blocks[0] as RichTextBlock.Heading
        assertEquals(4, heading0.level)
        assertEquals("Correct Answer Is D [ 61% ]", heading0.text.text)

        val para1 = blocks[1] as RichTextBlock.Paragraph
        val para1Urls = para1.text.getStringAnnotations("URL", 0, para1.text.length).map { it.item }
        assertEquals(0, para1Urls.size)
        assertTrue(para1.text.text.startsWith("Assessing serum osmolality is the first step"))

        val media2 = blocks[2] as RichTextBlock.Media
        assertEquals("big_6826eb649041f5.12084029.jpg", media2.mediaRef)
        assertEquals(200, media2.width)
        assertEquals(TextAlign.Center, media2.alignment)

        assertEquals("[ A ] [ 1% ]", (blocks[3] as RichTextBlock.Heading).text.text)
        assertEquals("[ B ] [ 23% ]", (blocks[5] as RichTextBlock.Heading).text.text)
        assertEquals("[ C ] [ 13% ]", (blocks[7] as RichTextBlock.Heading).text.text)
        assertEquals("[ E ] [ 2% ]", (blocks[9] as RichTextBlock.Heading).text.text)

        val paraE = blocks[10] as RichTextBlock.Paragraph
        val paraEUrls = paraE.text.getStringAnnotations("URL", 0, paraE.text.length).map { it.item }
        assertEquals(0, paraEUrls.size)
    }

    @Test
    fun testAmbossLearningCardsNotParsedAsLinks() {
        val snippet = """
            <p>Data attribute only: <span data-learningcard-id="123" data-anker="abc">Medical Term</span></p>
            <p>Angular learningcard: <a ng-click="toLearningcard('123','abc');" ng-href="{{ linkLearningcard('123','abc'); }}">Angular Term</a></p>
            <p>Valid external link: <a href="https://example.com/valid">External Site</a></p>
        """.trimIndent()

        val sanitized = HtmlUtils.sanitizeForRichText(snippet)
        val blocks = RichTextParser.parse(sanitized, dummyPalette, true)

        val allUrls = blocks.filterIsInstance<RichTextBlock.Paragraph>().flatMap { para ->
            para.text.getStringAnnotations("URL", 0, para.text.length).map { it.item }
        }

        assertEquals(listOf("https://example.com/valid"), allUrls)
    }
}
