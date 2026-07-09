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
}
