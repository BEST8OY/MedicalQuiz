package com.medqb.app.shared.ui.screens.filter

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterLabelsTest {

    @Test
    fun formatSelectionSubtitleHandlesAllCases() {
        assertEquals("All subjects", formatSelectionSubtitle(0, 0, "subject", "subjects"))
        assertEquals("1 subject selected", formatSelectionSubtitle(1, 0, "subject", "subjects"))
        assertEquals("3 selected", formatSelectionSubtitle(3, 0, "subject", "subjects"))
        assertEquals("All except 1 subject", formatSelectionSubtitle(0, 1, "subject", "subjects"))
        assertEquals("All except 2", formatSelectionSubtitle(0, 2, "subject", "subjects"))
        assertEquals("2 selected \u2022 1 excluded", formatSelectionSubtitle(2, 1, "subject", "subjects"))
    }

    @Test
    fun formatDifficultyLabelHandlesEmptyAndFullTiers() {
        assertEquals("All Difficulties", formatDifficultyLabel(emptySet()))
        assertEquals("All Difficulties", formatDifficultyLabel(DifficultyTier.entries.toSet()))
        assertEquals("Easy", formatDifficultyLabel(setOf(DifficultyTier.EASY)))
        assertEquals("Easy, Intermediate", formatDifficultyLabel(setOf(DifficultyTier.INTERMEDIATE, DifficultyTier.EASY)))
        assertEquals("3 selected", formatDifficultyLabel(setOf(DifficultyTier.EASY, DifficultyTier.INTERMEDIATE, DifficultyTier.DIFFICULT)))
    }

    @Test
    fun formatStatusFilterLabelHandlesVariousFilters() {
        assertEquals("All Questions", formatStatusFilterLabel(StatusFilter.ALL))
        assertEquals(
            "Not yet answered",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.NOT_YET_ANSWERED), StatusScope.ALL_ATTEMPTS))
        )
        assertEquals(
            "Last incorrect",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.ANSWERED_INCORRECTLY), StatusScope.LATEST_ATTEMPT))
        )
        assertEquals(
            "Ever incorrect",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.ANSWERED_INCORRECTLY), StatusScope.ALL_ATTEMPTS))
        )
        assertEquals(
            "Last correct",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.ANSWERED_CORRECTLY), StatusScope.LATEST_ATTEMPT))
        )
        assertEquals(
            "Ever correct",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.ANSWERED_CORRECTLY), StatusScope.ALL_ATTEMPTS))
        )
        assertEquals(
            "Marked",
            formatStatusFilterLabel(StatusFilter(setOf(QuestionStatus.MARKED), StatusScope.ALL_ATTEMPTS))
        )
        assertEquals(
            "Unanswered & Incorrect",
            formatStatusFilterLabel(
                StatusFilter(
                    setOf(QuestionStatus.NOT_YET_ANSWERED, QuestionStatus.ANSWERED_INCORRECTLY),
                    StatusScope.LATEST_ATTEMPT
                )
            )
        )
    }
}
