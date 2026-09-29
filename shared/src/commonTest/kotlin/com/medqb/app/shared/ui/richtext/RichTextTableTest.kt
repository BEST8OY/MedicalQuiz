package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import com.medqb.app.shared.ui.richtext.parser.RichTextParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        println("Column Count: ${table.columnCount}")
        println("Header Rows: ${table.headerRows.size}")
        println("Body Rows: ${table.bodyRows.size}")

        val renderModel = table.toRenderModel()
        println("RenderModel Column Count: ${renderModel.columnCount}")
        println("RenderModel Rows Count: ${renderModel.rows.size}")

        renderModel.rows.forEachIndexed { rowIndex, row ->
            val rowStr = row.cells.map { cell ->
                "[visible=${cell.isVisible}, colspan=${cell.columnSpan}, rowspan=${cell.rowSpan}, text=${cell.cell.text.text}]"
            }.joinToString(", ")
            println("Row $rowIndex: $rowStr")
        }

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
        val parts = com.medqb.app.shared.utils.HtmlUtils.extractQuestionHtmlParts(snippet)
        val blocks = RichTextParser.parse(parts.contentHtml, dummyPalette, true)
        val hintHtml = parts.hintHtml
        assertEquals(23, blocks.size)

        // Block 0: Heading "Patient Information"
        val heading0 = blocks[0] as RichTextBlock.Heading
        assertEquals(3, heading0.level)
        assertEquals("Patient Information", heading0.text.text)

        // Block 1: Paragraph "Age: 58 years" with bold "Age: " without background highlight
        val para1 = blocks[1] as RichTextBlock.Paragraph
        assertEquals("Age: 58\u00A0years", para1.text.text)
        val ageSpanStyle = para1.text.spanStyles.firstOrNull { it.item.fontWeight != null }
        assertNotNull(ageSpanStyle)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, ageSpanStyle.item.fontWeight)
        assertEquals(Color.Unspecified, ageSpanStyle.item.background)

        // Block 17: Heading "Physical Examination"
        val heading17 = blocks[17] as RichTextBlock.Heading
        assertEquals(3, heading17.level)
        assertEquals("Physical Examination", heading17.text.text)

        // Block 18: Vital signs table
        val table18 = blocks[18] as RichTextBlock.Table
        assertEquals(8, table18.columnCount)
        assertEquals(1, table18.headerRows.size)
        assertEquals(1, table18.bodyRows.size)

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

        // Block 19: Physical exam BulletList
        assertTrue(blocks[19] is RichTextBlock.BulletList)
        val bulletList19 = blocks[19] as RichTextBlock.BulletList
        assertEquals(5, bulletList19.items.size)

        // Block 20: Heading "Diagnostic Studies"
        val heading20 = blocks[20] as RichTextBlock.Heading
        assertEquals(3, heading20.level)
        assertEquals("Diagnostic Studies", heading20.text.text)

        // Block 21: Lab values table
        val table21 = blocks[21] as RichTextBlock.Table
        assertEquals(2, table21.columnCount)
        assertEquals(8, table21.bodyRows.size)

        // Block 22: Prompt paragraph
        val para22 = blocks[22] as RichTextBlock.Paragraph
        assertTrue(para22.text.text.startsWith("Repeat laboratory studies confirm hyponatremia."))

        // Hint assertions
        assertNotNull(hintHtml)
        val hintBlocks = RichTextParser.parse(hintHtml, dummyPalette, true)
        assertEquals(1, hintBlocks.size)
        val hintPara = hintBlocks[0] as RichTextBlock.Paragraph
        assertTrue(hintPara.text.text.contains("hyperglycemia"))
        assertTrue(hintPara.text.text.contains("hyponatremia"))

        val hyperglycemiaUrl = hintPara.text.getStringAnnotations("URL", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("3g0SE2") }
        assertNotNull(hyperglycemiaUrl)
        assertEquals("learningcard://3g0SE2/Z41765cf0097c7ea6f788430b14b10a35", hyperglycemiaUrl.item)

        val hyperglycemiaTooltip = hintPara.text.getStringAnnotations("TOOLTIP", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("elevated blood glucose") }
        assertNotNull(hyperglycemiaTooltip)

        val hyponatremiaUrl = hintPara.text.getStringAnnotations("URL", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("rg0f92") }
        assertNotNull(hyponatremiaUrl)
        assertEquals("learningcard://rg0f92/Z482f3cc1cfc32af07678821b7e06fb08", hyponatremiaUrl.item)

        val hyponatremiaTooltip = hintPara.text.getStringAnnotations("TOOLTIP", 0, hintPara.text.length)
            .firstOrNull { it.item.contains("serum sodium concentration") }
        assertNotNull(hyponatremiaTooltip)
    }

    @Test
    fun testAmbossExplanationParsing() {
        val snippet = """
<div><h4>Correct Answer Is D [ 61% ]</h4><p>
<span class="wichtig"><b>Assessing <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a></b></span> is the first step in evaluating confirmed <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a> to differentiate between <span class="wichtig"><b>hypotonic</b></span> (low <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>), <span class="wichtig"><b>hypertonic</b></span> (high <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>), and <span class="wichtig"><b>isotonic</b></span> (normal <span data-learningcard-id="860O5S" data-anker="Z0fbd33785ac393f41741addbbf8a9fa3" data-section-id="nuc77V0" data-sememe-id="jqc_yW0">serum osmolality</a>) <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">Hypotonic hyponatremia</a> is the most common type of <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. Further diagnostic evaluation of <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> includes assessing <span data-learningcard-id="kg0mv2" data-anker="Ze5c2933e2f4ca7e9f716818005ff069a" data-section-id="jJX_F_" data-sememe-id="Xqc9CW0">urine osmolality</a> (to determine <span data-learningcard-id="AT0Rt2" data-anker="Zbf62754ce74881b6657f0771bc811fd7" data-section-id="XK19U30" data-sememe-id="tKaXRl">antidiuretic hormone</a> activity), determining <span data-learningcard-id="fM0kLg" data-anker="Z67f90fc721c4b76620b3107bc207b164" data-section-id="891O6Q0">volume status</a> (to assess whether <span data-learningcard-id="AT0Rt2" data-anker="Zbf62754ce74881b6657f0771bc811fd7" data-section-id="XK19U30" data-sememe-id="tKaXRl">antidiuretic hormone</a> activity is appropriate), and assessing urinary <span data-sememe-id="arWQf50">sodium</span> and/or <span data-learningcard-id="kg0mv2" data-anker="Zd235f5f8bd029e49126829f0e80c2831" data-section-id="PJXW8_" data-sememe-id="Os0IEh">fractional excretion of sodium</a> (to determine if the cause is renal or extrarenal).
<br><br>This patient likely has <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> caused by <span data-learningcard-id="zT0rt2" data-anker="Z849dca7797cad193a13bd0c4d86128bc" data-sememe-id="uP0pgT">SIADH</a> following <span data-learningcard-id="_N05dg" data-anker="Zb1bb43280a4d0d86f2d53818b9e8afc8" data-section-id="LBXwY00" data-sememe-id="wQahy4">SSRI</a> initiation.</p><p></p></div><div style="align-items:center; text-align: center;"><img src="big_6826eb649041f5.12084029.jpg" width="200px" style="padding:20px;"></div>
<div><h4>[ A ] [ 1% ]</h4><p>
<span data-learningcard-id="gm0FUg" data-anker="Zc79a41be3ea73012acd816d5e2bf130d" data-section-id="WkcP5c0" data-sememe-id="t4aXOk">Furosemide</a> may be considered for the management of <span data-learningcard-id="rg0f92" data-anker="Z854654a69f8b6617bc9d64f46fe44b5a" data-section-id="8G0Oa3" data-sememe-id="4h13Vg0">hypervolemic hyponatremia</a>. This patient does not have symptoms of <span data-sememe-id="Dk11JS0">hypervolemia</span> (e.g., <span data-learningcard-id="KS0U0f" data-anker="Z48e76ffce1b364c1ba4dacdd79a56b7b" data-sememe-id="_405NT">ascites</a>, <span data-learningcard-id="SM0yLg" data-anker="Zc00dca4994157e86d8e6e8ee9510443f" data-section-id="JcXsWC" data-sememe-id="Oo0IXS">edema</a>) or features of conditions that cause <span data-learningcard-id="rg0f92" data-anker="Z854654a69f8b6617bc9d64f46fe44b5a" data-section-id="8G0Oa3" data-sememe-id="4h13Vg0">hypervolemic hyponatremia</a> like <span data-learningcard-id="rS0faf" data-anker="Zfdf718ee7030d25f9dfb6b6603941bd5" data-section-id="Slcywc0" data-sememe-id="pwdLPH0">congestive heart failure</a>, <span data-learningcard-id="PS0W-2" data-anker="Za99be06553ff299396a513bb65ed71be" data-sememe-id="D401lT">cirrhosis</a>, or <span data-learningcard-id="lg0vv2" data-anker="Zbdc3b2167c4bab75e0f1bf58faab9e22" data-section-id="l70vNh" data-sememe-id="qQ1CCg0">renal failure</a> with low urine output. <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">Hyponatremia</a> in this patient likely resulted from <span data-learningcard-id="zT0rt2" data-anker="Z849dca7797cad193a13bd0c4d86128bc" data-sememe-id="uP0pgT">SIADH</a> (triggered by <span data-learningcard-id="_N05dg" data-anker="Zb1bb43280a4d0d86f2d53818b9e8afc8" data-section-id="LBXwY00" data-sememe-id="wQahy4">SSRI</a> initiation), which typically manifests with <span data-learningcard-id="rg0f92" data-anker="Z83d9522134c8caaf6b03c6fa1566400a" data-section-id="8G0Oa3" data-sememe-id="Ph1WVg0">euvolemic hyponatremia</a>. Additional diagnostics are required to inform further management.</p><p></p></div>
<div><h4>[ B ] [ 23% ]</h4><p>
<span data-sememe-id="wk1hJS0">Hypertonic saline</span> is indicated in <span data-learningcard-id="rg0f92" data-anker="Zcd01c7bb7bc2292e790597f0e9da6d81" data-section-id="vG0Aa3" data-sememe-id="BM1zIh0">severely symptomatic hyponatremia</a> to increase serum <span data-sememe-id="arWQf50">sodium</span> rapidly to prevent <span data-learningcard-id="HL0K_g" data-anker="Zaca1b98782f4f2631c06f0414a59ee7b" data-section-id="-gcDBb0" data-sememe-id="a-0QDi">cerebral edema</a> and <span data-learningcard-id="HL0K_g" data-anker="Z7ca422322bb96d80a2b4054adfb6b1ff" data-section-id="sgct9b0" data-sememe-id="hSbczG">brain herniation</a>. Rapid <span data-sememe-id="arWQf50">sodium</span> correction is unnecessary and potentially harmful in patients with nonsevere symptoms due to the risk of overcorrection and subsequent <span data-learningcard-id="rg0f92" data-anker="Zf9d046b260590d60860353fc1977a8aa" data-section-id="cU1aXT0" data-sememe-id="ksYmEq">osmotic demyelination syndrome</a>. <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">Hyponatremia</a> with nonsevere symptoms (usually <span data-learningcard-id="rg0f92" data-anker="Z4cf45c6982fded7aca34453024148d43" data-section-id="tG0Xa3" data-sememe-id="uM1pqh0">chronic hyponatremia</span>) requires a slow <span data-sememe-id="arWQf50">sodium</span> correction rate.</p><p></p></div>
<div><h4>[ C ] [ 13% ]</h4><p>The <span data-learningcard-id="kg0mv2" data-anker="Zd235f5f8bd029e49126829f0e80c2831" data-section-id="PJXW8_" data-sememe-id="Os0IEh">fractional excretion of sodium</a> should be determined in patients with <span data-learningcard-id="rg0f92" data-anker="Z76048ccb46e88843939709cf6efd2ab6" data-section-id="8G0Oa3" data-sememe-id="Lrcw3d0">hypotonic hyponatremia</a> to assess whether the cause is renal or extrarenal. However, first, it is important to establish whether the patient has hypotonic, isotonic, or <span data-learningcard-id="rg0f92" data-anker="Zcb41877d9f5bbc450e14fef0a51d0027" data-section-id="8G0Oa3" data-sememe-id="KrcURd0">hypertonic hyponatremia</a>.</p><p></p></div>
<div><h4>[ E ] [ 2% ]</h4><p>Measuring serum <span data-learningcard-id="AT0Rt2" data-anker="Zbc03040ec893396d15c13d0b69e3dba4" data-section-id="XK19U30" data-sememe-id="jKa_Tl">TSH</span> and <span data-learningcard-id="V60GPS" data-anker="Z68437810c311fa1e0e910fecfa30be04" data-section-id="BtczfV0" data-sememe-id="-KaDQl">cortisol</span> levels may be appropriate later in the workup as both <span data-learningcard-id="cg0a82" data-anker="Z3fd41eef8b8ce3cec9c7eda727b6d1ce" data-sememe-id="CP0qST">hypothyroidism</a> and <span data-learningcard-id="Ug0bu2" data-anker="Z351bdb8ac6670de066f05e6a1a8a74a2" data-section-id="HJ0KvS" data-sememe-id="TlX6wy">hypocortisolism</a> can manifest with <span data-learningcard-id="rg0f92" data-anker="Z482f3cc1cfc32af07678821b7e06fb08" data-sememe-id="n_Y7K7">hyponatremia</a>. A different diagnostic study is more appropriate at this stage.</p><p></p></div>
"""
        val sanitized = com.medqb.app.shared.utils.HtmlUtils.sanitizeForRichText(snippet)
        val blocks = RichTextParser.parse(sanitized, dummyPalette, true)
        assertEquals(11, blocks.size)

        // Block 0: Heading 4 "Correct Answer Is D [ 61% ]"
        val heading0 = blocks[0] as RichTextBlock.Heading
        assertEquals(4, heading0.level)
        assertEquals("Correct Answer Is D [ 61% ]", heading0.text.text)

        // Block 1: Explanation paragraph with learning card URLs and bold styling
        val para1 = blocks[1] as RichTextBlock.Paragraph
        val para1Urls = para1.text.getStringAnnotations("URL", 0, para1.text.length).map { it.item }
        assertEquals(17, para1Urls.size)
        assertEquals("learningcard://860O5S/Z0fbd33785ac393f41741addbbf8a9fa3", para1Urls[0])
        assertEquals("learningcard://rg0f92/Z482f3cc1cfc32af07678821b7e06fb08", para1Urls[1])
        assertTrue(para1.text.text.startsWith("Assessing serum osmolality is the first step"))

        // Block 2: Image Media block with width constraint and centering
        val media2 = blocks[2] as RichTextBlock.Media
        assertEquals("big_6826eb649041f5.12084029.jpg", media2.mediaRef)
        assertEquals(200, media2.width)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, media2.alignment)

        // Option Headings
        val headingA = blocks[3] as RichTextBlock.Heading
        assertEquals(4, headingA.level)
        assertEquals("[ A ] [ 1% ]", headingA.text.text)

        val headingB = blocks[5] as RichTextBlock.Heading
        assertEquals(4, headingB.level)
        assertEquals("[ B ] [ 23% ]", headingB.text.text)

        val headingC = blocks[7] as RichTextBlock.Heading
        assertEquals(4, headingC.level)
        assertEquals("[ C ] [ 13% ]", headingC.text.text)

        val headingE = blocks[9] as RichTextBlock.Heading
        assertEquals(4, headingE.level)
        assertEquals("[ E ] [ 2% ]", headingE.text.text)

        // Option paragraphs with isolated learning cards
        val paraE = blocks[10] as RichTextBlock.Paragraph
        val paraEUrls = paraE.text.getStringAnnotations("URL", 0, paraE.text.length).map { it.item }
        assertEquals(5, paraEUrls.size)
        assertEquals("learningcard://AT0Rt2/Zbc03040ec893396d15c13d0b69e3dba4", paraEUrls[0])
        assertEquals("learningcard://V60GPS/Z68437810c311fa1e0e910fecfa30be04", paraEUrls[1])
        assertEquals("learningcard://cg0a82/Z3fd41eef8b8ce3cec9c7eda727b6d1ce", paraEUrls[2])
        assertEquals("learningcard://Ug0bu2/Z351bdb8ac6670de066f05e6a1a8a74a2", paraEUrls[3])
        assertEquals("learningcard://rg0f92/Z482f3cc1cfc32af07678821b7e06fb08", paraEUrls[4])

        // Verify web URL resolution
        val resolvedWebUrl = com.medqb.app.shared.utils.HtmlUtils.resolveWebUrl("learningcard://860O5S/Z0fbd33785ac393f41741addbbf8a9fa3")
        assertEquals("https://next.amboss.com/us/article/860O5S#Z0fbd33785ac393f41741addbbf8a9fa3", resolvedWebUrl)

        val regularWebUrl = com.medqb.app.shared.utils.HtmlUtils.resolveWebUrl("https://example.com/test")
        assertEquals("https://example.com/test", regularWebUrl)
    }
}

