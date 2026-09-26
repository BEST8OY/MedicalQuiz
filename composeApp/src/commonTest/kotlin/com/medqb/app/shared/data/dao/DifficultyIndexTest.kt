package com.medqb.app.shared.data.dao

import com.medqb.app.shared.data.database.DifficultyTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DifficultyIndexTest {

    @Test
    fun partitions100QuestionsAccordingToExactPercentileTiers() {
        // 100 questions with success rates from 0.01 to 1.00
        val stats = (1L..100L).map { id -> id to (id / 100.0) }
        val index = DifficultyIndex.build(stats)

        assertTrue(index.isAvailable)
        assertEquals(5, index.tierCounts[DifficultyTier.VERY_DIFFICULT])
        assertEquals(15, index.tierCounts[DifficultyTier.DIFFICULT])
        assertEquals(30, index.tierCounts[DifficultyTier.INTERMEDIATE])
        assertEquals(30, index.tierCounts[DifficultyTier.EASY])
        assertEquals(20, index.tierCounts[DifficultyTier.VERY_EASY])

        // Check boundaries
        assertEquals(DifficultyTier.VERY_DIFFICULT, index.getTier(1L))
        assertEquals(DifficultyTier.VERY_DIFFICULT, index.getTier(5L))
        assertEquals(DifficultyTier.DIFFICULT, index.getTier(6L))
        assertEquals(DifficultyTier.DIFFICULT, index.getTier(20L))
        assertEquals(DifficultyTier.INTERMEDIATE, index.getTier(21L))
        assertEquals(DifficultyTier.INTERMEDIATE, index.getTier(50L))
        assertEquals(DifficultyTier.EASY, index.getTier(51L))
        assertEquals(DifficultyTier.EASY, index.getTier(80L))
        assertEquals(DifficultyTier.VERY_EASY, index.getTier(81L))
        assertEquals(DifficultyTier.VERY_EASY, index.getTier(100L))
    }

    @Test
    fun emptyOrAllFilterMatchesEverything() {
        val stats = (1L..100L).map { id -> id to (id / 100.0) }
        val index = DifficultyIndex.build(stats)

        // Empty filter should match all
        assertTrue(index.matches(1L, emptySet()))
        assertTrue(index.matches(50L, emptySet()))

        // Full set should match all
        assertTrue(index.matches(1L, DifficultyTier.entries.toSet()))
        assertTrue(index.matches(50L, DifficultyTier.entries.toSet()))
    }

    @Test
    fun specificTiersFilterCorrectly() {
        val stats = (1L..100L).map { id -> id to (id / 100.0) }
        val index = DifficultyIndex.build(stats)

        val hardFilter = setOf(DifficultyTier.VERY_DIFFICULT, DifficultyTier.DIFFICULT)

        assertTrue(index.matches(1L, hardFilter))   // VERY_DIFFICULT
        assertTrue(index.matches(10L, hardFilter))  // DIFFICULT
        assertFalse(index.matches(50L, hardFilter)) // INTERMEDIATE
        assertFalse(index.matches(90L, hardFilter)) // VERY_EASY
    }

    @Test
    fun uniformDummyStatsMarkIndexUnavailable() {
        // e.g. NBME emergency medicine where all questions have 1.0 / 100.0
        val stats = (1L..50L).map { id -> id to 0.01 }
        val index = DifficultyIndex.build(stats)

        assertFalse(index.isAvailable)
        // When unavailable, matches should return true for any query
        assertTrue(index.matches(1L, setOf(DifficultyTier.VERY_DIFFICULT)))
    }

    @Test
    fun smallStatsUnder5QuestionsMarkIndexUnavailable() {
        val stats = listOf(1L to 0.2, 2L to 0.5)
        val index = DifficultyIndex.build(stats)

        assertFalse(index.isAvailable)
    }
}
