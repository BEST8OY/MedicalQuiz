package com.medqb.app.shared.ui.screens.quiz

import com.medqb.app.shared.data.models.Question
import kotlin.test.Test
import kotlin.test.assertEquals

class QuizMetadataCardTest {

    private fun sampleQuestion(
        subNames: List<String> = emptyList(),
        sysNames: List<String> = emptyList(),
    ) = Question(
        id = 42L,
        question = "",
        explanation = "",
        corrAns = 1,
        title = null,
        mediaName = null,
        otherMedias = null,
        pplTaken = null,
        corrTaken = null,
        subId = null,
        sysId = null,
        subNames = subNames,
        sysNames = sysNames,
    )

    @Test
    fun singleSystemWithCommasRemainsOneChipWithSingularLabel() {
        val sections = computeMetadataSections(
            sampleQuestion(
                sysNames = listOf("Blood, Lymphatic, and Immune Systems")
            )
        )

        val systemSection = sections.filterIsInstance<MetadataSection.Chips>()
            .single { it.label == "System" || it.label == "Systems" }

        assertEquals("System", systemSection.label)
        assertEquals(listOf("Blood, Lymphatic, and Immune Systems"), systemSection.values)
    }

    @Test
    fun multipleSystemsWithCommasRemainDistinctIntactChips() {
        val sections = computeMetadataSections(
            sampleQuestion(
                subNames = listOf("Internal Medicine", "Pathology, Genetics"),
                sysNames = listOf(
                    "Ear, Nose, and Throat",
                    "Blood, Lymphatic, and Immune Systems",
                ),
            )
        )

        val subjectSection = sections.filterIsInstance<MetadataSection.Chips>()
            .single { it.label == "Subjects" }
        assertEquals(listOf("Internal Medicine", "Pathology, Genetics"), subjectSection.values)

        val systemSection = sections.filterIsInstance<MetadataSection.Chips>()
            .single { it.label == "Systems" }
        assertEquals(
            listOf("Ear, Nose, and Throat", "Blood, Lymphatic, and Immune Systems"),
            systemSection.values,
        )
    }
}
