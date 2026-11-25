package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import com.medqb.app.shared.ui.richtext.parser.RichTextParser
import com.medqb.app.shared.ui.richtext.parser.RichTextParserConfig
import com.medqb.app.shared.utils.HtmlUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextTableTest {

    private val dummyPalette = RichTextPalette(
        importantBackground = Color.Red,
        importantText = Color.Black,
        selectedBackground = Color.Blue,
        selectedText = Color.White,
        linkText = Color.Blue,
        dictionaryText = Color.Green,
        abstractText = Color.Gray
    )

    @Test
    fun testParseTableWithRowspanAndColspan() {
        val html = """
            <table class="table-default-style table-header-footer-style">
                <tbody>
                    <tr>
                        <td colspan="3">
                            <p align="center"><strong>Prevention of calcium stone (calcium oxalate, calcium phosphate) recurrence</strong></p>
                        </td>
                    </tr>
                    <tr>
                        <td></td>
                        <td>
                            <p align="center"><strong>Intervention</strong></p>
                        </td>
                        <td>
                            <p align="center"><strong>Mechanism</strong></p>
                        </td>
                    </tr>
                    <tr>
                        <td rowspan="9">
                            <p align="center"><strong>Dietary<br />interventions</strong></p>
                        </td>
                        <td colspan="2">
                            <p><strong>All calcium stones:</strong></p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Fluid (produce &gt;2 L/day urine)</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Urine flow, &darr; solute concentration</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&darr; Sodium (&lt;2,300 mg/day)</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Renal calcium reabsorption</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Citrate (fruits &amp; vegetables)</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">Binds urinary calcium to inhibit stone formation</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Potassium</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Urinary citrate excretion</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&darr; Animal protein</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&darr; Urinary calcium excretion</p>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <p><strong>Calcium oxalate stones:</strong></p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">Adequate calcium intake (1,200 mg/day)</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&darr; Oxalate absorption in GI tract</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">&darr; Oxalate (spinach)</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&darr; Urinary oxalate excretion</p>
                        </td>
                    </tr>
                    <tr>
                        <td rowspan="2">
                            <p align="center"><strong>Pharmacologic<br />interventions</strong></p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">Thiazide diuretics</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Renal calcium reabsorption</p>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <p style="margin-left:.25in;">Potassium citrate</p>
                        </td>
                        <td>
                            <p style="margin-left:.25in;">&uarr; Urinary citrate concentration</p>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="3">
                            <p><strong>GI</strong> = gastrointestinal.</p>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, false)
        assertEquals(1, blocks.size)
        val table = blocks.first() as RichTextBlock.Table
        val renderModel = table.toRenderModel()

        // Column and row counts
        assertEquals(3, table.columnCount)
        assertEquals(14, renderModel.rows.size)

        // Row 0 spanning 3 columns
        val row0 = renderModel.rows[0]
        assertEquals(1, row0.cells.size)
        assertEquals(true, row0.cells[0].isVisible)
        assertEquals(3, row0.cells[0].columnSpan)
        assertEquals(1, row0.cells[0].rowSpan)

        // Row 2: "Dietary interventions" rowspan=9
        val row2 = renderModel.rows[2]
        assertEquals(2, row2.cells.size)
        assertEquals(true, row2.cells[0].isVisible)
        assertEquals(1, row2.cells[0].columnSpan)
        assertEquals(9, row2.cells[0].rowSpan)
        assertTrue(row2.cells[0].cell.text.text.contains("Dietary"))

        // Row 3: rowspan=9 continuation (invisible placeholder)
        val row3 = renderModel.rows[3]
        assertEquals(3, row3.cells.size)
        assertEquals(false, row3.cells[0].isVisible)
        assertEquals(1, row3.cells[0].columnSpan)
        assertEquals(9, row3.cells[0].rowSpan)

        // Row 11: "Pharmacologic interventions" rowspan=2
        val row11 = renderModel.rows[11]
        assertEquals(true, row11.cells[0].isVisible)
        assertEquals(1, row11.cells[0].columnSpan)
        assertEquals(2, row11.cells[0].rowSpan)

        // Row 12: continuation
        val row12 = renderModel.rows[12]
        assertEquals(false, row12.cells[0].isVisible)
        assertEquals(1, row12.cells[0].columnSpan)
        assertEquals(2, row12.cells[0].rowSpan)
    }

    @Test
    fun testAmbossQuestionParsing() {
        val snippet = """
<div><style>
.nowrap {
    white-space: nowrap;
}

.scientific-name {
    font-style: italic;
}

.wichtig {
    font-weight: bold;
}
</style>












<div class="abstract">
<h3>Patient Information</h3>
<p><span class="wichtig">Age: </span><span class="nowrap">58 years</span></p>
<p><span class="wichtig">Gender: </span>M, self-identified</p>
<p><span class="wichtig">Race/Ethnicity: </span>unspecified</p>
<p><span class="wichtig">Site of Care: </span>emergency department</p>
<h3>History</h3>
<p><span class="wichtig">Reason for Visit/Chief Concern:</span> “I have had this headache for the past <span class="nowrap">3 days</span> and I can't get rid of it.”</p>
<p><span class="wichtig">History of Present Illness:</span></p>
<ul>
<li>
<span class="nowrap">3-day</span> history of headache and dizziness</li>
	<li>associated with muscle weakness in the upper and lower extremities</li>
	<li>diagnosed with major depressive disorder <span class="nowrap">3 weeks</span> ago</li>
</ul>
<p><span class="wichtig">Past Medical History:</span></p>
<ul>
<li>major depressive disorder</li>
</ul>
<p><span class="wichtig">Medications:</span></p>
<ul>
<li>sertraline</li>
</ul>
<p><span class="wichtig">Allergies:</span></p>
<ul>
<li>no known drug allergies</li>
</ul>
<p><span class="wichtig">Psychosocial History:</span></p>
<ul>
<li>does not smoke cigarettes, drink alcoholic beverages, or use other substances</li>
	<li>lives with his wife</li>
</ul>
<h3>Physical Examination</h3>
</div>
<div class="modal-overflow-scroll"><table>
<thead class="abstract"><tr>
<th>Temp</th>
			<th>Pulse</th>
			<th>Resp</th>
			<th>BP</th>
			<th>O<sub>2</sub> Sat</th>
			<th>Ht</th>
			<th>Wt</th>
			<th>BMI</th>
		</tr></thead>
<tbody><tr>
<td colspan="1" rowspan="2">
<span class="abstract">36.2°C</span><br><span class="abstract">(97.2°F)</span>
</td>
			
			
			
			
			<div class="abstract">
<td colspan="1" rowspan="2">105/min</td>
<td colspan="1" rowspan="2">19/min</td>
<td colspan="1" rowspan="2">110/75 mm Hg</td>
<td colspan="1" rowspan="2">-</td>
</div>
<td colspan="1" rowspan="2">
<span class="abstract">185 cm</span><br><span class="abstract">(6 ft 1 in)</span>
</td>
			<td colspan="1" rowspan="2">
<span class="abstract">70 kg</span><br><span class="abstract">(154 lb)</span>
</td>
			<td colspan="1" rowspan="2" class="abstract">20.5 kg/m<sup>2</sup>
</td>
		</tr></tbody>
</table></div>
<div class="abstract">
<ul>
<li>Appearance: alert</li>
	<li>Pulmonary: clear lung fields</li>
	<li>Cardiac: tachycardia; regular rhythm </li>
	<li>Abdominal: soft and nontender; no hepatosplenomegaly </li>
	<li>Neurologic: cranial nerves grossly intact; sensation and proprioception intact; 4/5 muscle strength in upper and lower limbs; fine and gross motor coordination intact; gait unsteady </li>
</ul>
<h3>Diagnostic Studies</h3>
<div class="modal-overflow-scroll"><table><tbody>
<tr>
<td>Serum</td>
			<td></td>
		</tr>
<tr>
<td style="padding-left: 1em;">Na<sup>+</sup>
</td>
			<td><span class="selected">122 mEq/L</span></td>
		</tr>
<tr>
<td style="padding-left: 1em;">K<sup>+</sup>
</td>
			<td>4.2 mEq/L</td>
		</tr>
<tr>
<td style="padding-left: 1em;">Cl<sup>-</sup>
</td>
			<td>98 mEq/L</td>
		</tr>
<tr>
<td style="padding-left: 1em;">HCO<sub>3</sub><sup>-</sup>
</td>
			<td>22 mEq/L</td>
		</tr>
<tr>
<td style="padding-left: 1em;">Urea nitrogen</td>
			<td>12 mg/dL</td>
		</tr>
<tr>
<td style="padding-left: 1em;">Creatinine</td>
			<td>0.9 mg/dL</td>
		</tr>
<tr>
<td style="padding-left: 1em;">Glucose</td>
			<td>100 mg/dL</td>
		</tr>
</tbody></table></div>
</div>
<p>Repeat laboratory studies confirm hyponatremia. Which of the following is the most appropriate next step in management?</p>
</div><button style="margin-bottom: 20px" onclick="var x = document.getElementById('hintdiv');if (x.style.display === 'none') {x.style.display = 'block'; } else {x.style.display = 'none';}">Show Hint</button><div id="hintdiv" style="display:none;"><p>After excluding <a ng-click="toLearningcard('3g0SE2','Z41765cf0097c7ea6f788430b14b10a35',${'$'}event);" ng-href="{{ linkLearningcard('3g0SE2','Z41765cf0097c7ea6f788430b14b10a35'); }}" class="autolink" data-learningcard-id="3g0SE2" data-anker="Z41765cf0097c7ea6f788430b14b10a35" data-learningcard-id="3g0SE2" data-section-id="8I0Oeh" miamed-smartip='{"master_phrase":"Classic symptoms of hyperglycemia","translation":"","synonym":["Hyperglycemia"],"description":"A condition of elevated blood glucose levels. Classically causes polyuria, polydipsia, and polyphagia.","destinations":[{"label":"Diabetes mellitus \u2192 Clinical features","learning_card_xid":"3g0SE2","anchor_hash":"Z41765cf0097c7ea6f788430b14b10a35"},{"label":"Inpatient management of hyperglycemia \u2192 Summary","learning_card_xid":"1q02xS","anchor_hash":"Zf53b332e5515f8665a7edc7633691a44"}]}' data-phrasegroup-id="41W3S40" id="L3e0452a196cb5e88c42240f3cc33ec4a" data-source="L3e0452a196cb5e88c42240f3cc33ec4a">hyperglycemia</a>, additional management depends on an important first step in evaluating <span class="wichtig">confirmed <a ng-click="toLearningcard('rg0f92','Z482f3cc1cfc32af07678821b7e06fb08',${'$'}event);" ng-href="{{ linkLearningcard('rg0f92','Z482f3cc1cfc32af07678821b7e06fb08'); }}" class="autolink" data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-learningcard-id="rg0f92" miamed-smartip='{"master_phrase":"Hyponatremia","translation":"","synonym":[],"description":"A serum sodium concentration of &lt; 135 mEq\/L (some sources may use a cutoff of &lt; 136 mEq\/L). Etiologies include thiazide diuretic use, adrenal insufficiency, and syndrome of inappropriate antidiuretic hormone secretion. Manifestations include altered mental status, nausea, vomiting, muscle weakness, and hyporeflexia. If severe, can cause seizures, coma, and death.","destinations":[{"label":"Hyponatremia","learning_card_xid":"rg0f92","anchor_hash":"Z482f3cc1cfc32af07678821b7e06fb08"}]}' data-phrasegroup-id="n_Y7K7" id="L7050a76e36a047e64ccc69ad3fcd37a3" data-source="L7050a76e36a047e64ccc69ad3fcd37a3">hyponatremia</a></span>.</p></div>
"""
        val parts = HtmlUtils.extractQuestionHtmlParts(snippet)
        val blocks = RichTextParser.parse(parts.contentHtml, dummyPalette, true)
        val hintHtml = parts.hintHtml
        assertEquals(23, blocks.size)

        val heading0 = blocks[0] as RichTextBlock.Heading
        assertEquals(3, heading0.level)
        assertEquals("Patient Information", heading0.text.text)

        val para1 = blocks[1] as RichTextBlock.Paragraph
        assertEquals("Age: 58\u00A0years", para1.text.text)
        val ageSpanStyle = para1.text.spanStyles.firstOrNull { it.item.fontWeight != null }
        assertNotNull(ageSpanStyle)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, ageSpanStyle.item.fontWeight)
        assertEquals(Color.Unspecified, ageSpanStyle.item.background)

        val heading17 = blocks[17] as RichTextBlock.Heading
        assertEquals(3, heading17.level)
        assertEquals("Physical Examination", heading17.text.text)

        val table18 = blocks[18] as RichTextBlock.Table
        assertEquals(8, table18.columnCount)
        assertEquals(1, table18.rows.count { it.isHeader })
        assertEquals(1, table18.rows.count { !it.isHeader })

        val vitalRenderModel = table18.toRenderModel()
        assertEquals(2, vitalRenderModel.rows.size)
        val bodyRow = vitalRenderModel.rows[1]
        assertEquals(8, bodyRow.cells.size)
        bodyRow.cells.forEach { cell ->
            assertEquals(1, cell.rowSpan, "Single-row table cell must have rowSpan clamped to 1")
            assertEquals(true, cell.isVisible, "All cells in single-row table must be visible")
        }
        assertEquals("36.2°C\n(97.2°F)", bodyRow.cells[0].cell.text.text)
        assertEquals("105/min", bodyRow.cells[1].cell.text.text)

        assertTrue(blocks[19] is RichTextBlock.BulletList)
        val bulletList19 = blocks[19] as RichTextBlock.BulletList
        assertEquals(5, bulletList19.items.size)

        val heading20 = blocks[20] as RichTextBlock.Heading
        assertEquals(3, heading20.level)
        assertEquals("Diagnostic Studies", heading20.text.text)

        val table21 = blocks[21] as RichTextBlock.Table
        assertEquals(2, table21.columnCount)
        assertEquals(8, table21.rows.count { !it.isHeader })

        val para22 = blocks[22] as RichTextBlock.Paragraph
        assertTrue(para22.text.text.startsWith("Repeat laboratory studies confirm hyponatremia."))

        assertNotNull(hintHtml)
        val hintBlocks = RichTextParser.parse(hintHtml, dummyPalette, true)
        assertEquals(1, hintBlocks.size)
        val hintPara = hintBlocks[0] as RichTextBlock.Paragraph
        assertTrue(hintPara.text.text.contains("hyperglycemia"))
        assertTrue(hintPara.text.text.contains("hyponatremia"))

        val hyperglycemiaUrl = hintPara.text.getStringAnnotations("URL", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("3g0SE2") }
        assertNull(hyperglycemiaUrl)

        val hyperglycemiaTooltip = hintPara.text.getStringAnnotations("TOOLTIP", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("elevated blood glucose") }
        assertNotNull(hyperglycemiaTooltip)

        val hyponatremiaUrl = hintPara.text.getStringAnnotations("URL", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("rg0f92") }
        assertNull(hyponatremiaUrl)

        val hyponatremiaTooltip = hintPara.text.getStringAnnotations("TOOLTIP", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("serum sodium concentration") }
        assertNotNull(hyponatremiaTooltip)
    }

    @Test
    fun tableColumnWeightsResolveTableWideEvenWhenOnlyHeaderHasWidths() {
        val html = """
            <table>
                <thead>
                    <tr><th width="100">Col A</th><th width="300">Col B</th></tr>
                </thead>
                <tbody>
                    <tr><td>Val 1</td><td>Val 2</td></tr>
                </tbody>
            </table>
        """.trimIndent()
        val table = RichTextParser.parse(html, dummyPalette, false).single() as RichTextBlock.Table
        val model = table.toRenderModel()
        assertEquals(listOf(100f, 300f), model.columnWeights)
    }

    @Test
    fun midTableSubheaderStaysInPlaceAndSpacerRowPreservesRowspan() {
        val html = """
            <table>
                <thead>
                    <tr><th>Col1</th><th>Col2</th></tr>
                </thead>
                <tbody>
                    <tr><td rowspan="3">Span</td><td>Row 1</td></tr>
                    <tr><td></td></tr>
                    <tr><td>Row 3</td></tr>
                    <tr class="subheader"><td colspan="2">Mid-table Section</td></tr>
                    <tr><td>After</td><td>Row 4</td></tr>
                </tbody>
            </table>
        """.trimIndent()

        val blocks = RichTextParser.parse(html, dummyPalette, showSelectedHighlight = false)
        val table = blocks.single() as RichTextBlock.Table
        val model = table.toRenderModel()

        // 1 thead + 5 tbody rows = 6 rows in exact DOM order
        assertEquals(6, model.rows.size)
        assertEquals("Col1", model.rows[0].cells[0].cell.text.text)
        // Row 2 is empty spacer row inside rowspan=3 -> still has continuation placeholder at col 0
        assertEquals(false, model.rows[2].cells[0].isVisible)
        // Row 3 has "Row 3" at col 1 (NOT shifted into col 0, and rowspan ended at row 3)
        assertEquals("Row 3", model.rows[3].cells[1].cell.text.text)
        // Row 4 is the mid-table subheader IN ITS EXACT POSITION (not teleported to top)
        assertTrue(model.rows[4].isHeaderRow)
        assertEquals("Mid-table Section", model.rows[4].cells[0].cell.text.text)
        assertEquals("After", model.rows[5].cells[0].cell.text.text)
    }

    @Test
    fun maxTableColumnsCapIsEnforcedInRenderModel() {
        val cells = (1..(RichTextParserConfig.MAX_TABLE_COLUMNS + 15)).joinToString("") { "<td>$it</td>" }
        val blocks = RichTextParser.parse("<table><tr>$cells</tr></table>", dummyPalette, false)
        val table = blocks.single() as RichTextBlock.Table
        val model = table.toRenderModel()
        assertEquals(RichTextParserConfig.MAX_TABLE_COLUMNS, table.columnCount)
        assertEquals(RichTextParserConfig.MAX_TABLE_COLUMNS, model.columnCount)
    }

    @Test
    fun sparseRowBeforeActiveRowspanEmitsPlaceholderCell() {
        val html = """
            <table>
                <tr><td>A1</td><td rowspan="2">B1-2</td></tr>
                <tr></tr>
            </table>
        """.trimIndent()
        val blocks = RichTextParser.parse(html, dummyPalette, false)
        val table = blocks.single() as RichTextBlock.Table
        val model = table.toRenderModel()
        assertEquals(2, model.rows.size)
        // Row 1 had no explicit <td>, so col 0 should get an invisible spacer and col 1 the rowspan continuation
        assertEquals(2, model.rows[1].cells.size)
        assertEquals(false, model.rows[1].cells[0].isVisible)
        assertEquals(1, model.rows[1].cells[0].columnSpan)
        assertEquals(false, model.rows[1].cells[1].isVisible)
        assertEquals(2, model.rows[1].cells[1].rowSpan)
    }
}
