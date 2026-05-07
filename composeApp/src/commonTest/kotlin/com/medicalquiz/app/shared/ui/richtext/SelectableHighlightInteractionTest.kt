package com.medicalquiz.app.shared.ui.richtext

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectableHighlightInteractionTest {
    @Test
    fun expandToWordBoundariesKeepsMedicalConnectorsInsideSelection() {
        assertSelectedText("Treat COVID-19 with β-blocker therapy", "COVID", "COVID-19")
        assertSelectedText("Check mg/dL before dosing", "dL", "mg/dL")
        assertSelectedText("HbA1c is 7.2% today", "7.2", "7.2")
        assertSelectedText("Na+ channels differ from C++ examples", "Na", "Na+")
        assertSelectedText("Na+ channels differ from C++ examples", "C++", "C++")
    }

    @Test
    fun expandToWordBoundariesTrimsWrappingAndSentencePunctuation() {
        assertSelectedText("Find (hypertension), then review.", "hyper", "hypertension")
        assertSelectedText("Patient said “asthma.”", "asthma", "asthma")
        assertSelectedText("Don't split contractions or possessives", "Don", "Don't")
    }

    @Test
    fun expandToWordBoundariesSelectsEllipsesAndPunctuationClustersDirectly() {
        assertSelectedText("Wait... then continue", "...", "...")
        assertSelectedText("Wait… then continue", "…", "…")
        assertSelectedText("Use — not hyphen", "—", "—")
    }

    @Test
    fun expandToWordBoundariesDoesNotJumpAcrossWhitespace() {
        val text = "alpha     beta"
        val offset = text.indexOf("     ") + 2

        assertEquals(" ", text.selectedAt(offset))
    }

    private fun assertSelectedText(text: String, needle: String, expected: String) {
        val offset = text.indexOf(needle)
        check(offset >= 0) { "Needle '$needle' was not found in '$text'" }

        assertEquals(expected, text.selectedAt(offset))
    }

    private fun String.selectedAt(offset: Int): String {
        val (start, end) = expandToWordBoundaries(this, offset)
        return substring(start, end)
    }
}
