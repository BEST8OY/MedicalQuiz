package com.medqb.app.shared.domain

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.viewmodel.FakeDatabaseProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplyFiltersUseCaseTest {

    private val useCase = ApplyFiltersUseCase()

    @Test
    fun pruneSystemsForSubjectsPreservesSelectionWhenSubjectsAreEmpty() = runTest {
        val db = FakeDatabaseProvider()
        val pruned = useCase.pruneSystemsForSubjects(
            db = db,
            newSubjectIds = emptySet(),
            previouslySelectedSystems = setOf(10L, 20L),
        )
        assertEquals(setOf(10L, 20L), pruned)
    }

    @Test
    fun pruneSystemsForSubjectsRetainsOnlyValidSystemsForSubject() = runTest {
        val db = FakeDatabaseProvider().apply {
            seededSystems = listOf(
                System(id = 10, name = "Cardio", count = 5),
                System(id = 30, name = "Renal", count = 3),
            )
        }
        val pruned = useCase.pruneSystemsForSubjects(
            db = db,
            newSubjectIds = setOf(1L),
            previouslySelectedSystems = setOf(10L, 20L, 30L),
        )
        // 20L is not in seededSystems, so only 10L and 30L are retained
        assertEquals(setOf(10L, 30L), pruned)
    }

    @Test
    fun pruneSystemsWithNullDbReturnsEmptySetWhenSubjectsSelected() = runTest {
        val pruned = useCase.pruneSystemsForSubjects(
            db = null,
            newSubjectIds = setOf(1L),
            previouslySelectedSystems = setOf(10L),
        )
        assertTrue(pruned.isEmpty())
    }

    @Test
    fun normalizeSelectedSystemsFiltersOutUnavailableSystems() = runTest {
        val db = FakeDatabaseProvider().apply {
            seededSystems = listOf(
                System(id = 101, name = "GI", count = 12),
            )
        }
        val normalized = useCase.normalizeSelectedSystems(
            db = db,
            selectedSubjectIds = setOf(2L),
            newSystemIds = setOf(101L, 102L),
        )
        assertEquals(setOf(101L), normalized)
    }

    @Test
    fun normalizeSelectedSystemsWithNullDbReturnsEmptySet() = runTest {
        val normalized = useCase.normalizeSelectedSystems(
            db = null,
            selectedSubjectIds = setOf(2L),
            newSystemIds = setOf(101L),
        )
        assertTrue(normalized.isEmpty())
    }

    @Test
    fun previewQuestionCountDelegatesToDatabaseProvider() = runTest {
        var receivedPerfFilter: PerformanceFilter? = null
        val db = FakeDatabaseProvider().apply {
            countQuestionIdsProvider = { subjects, _, systems, _, perf, tiers ->
                receivedPerfFilter = perf
                if (subjects?.contains(1L) == true) 42 else 0
            }
        }

        val count = useCase.previewQuestionCount(
            db = db,
            selectedSubjectIds = setOf(1L),
            selectedSystemIds = setOf(10L),
            performanceFilter = PerformanceFilter.LAST_INCORRECT,
            difficultyFilters = setOf(DifficultyTier.DIFFICULT),
        )

        assertEquals(42, count)
        assertEquals(PerformanceFilter.LAST_INCORRECT, receivedPerfFilter)
    }

    @Test
    fun previewQuestionCountReturnsZeroWhenDbIsNull() = runTest {
        val count = useCase.previewQuestionCount(
            db = null,
            selectedSubjectIds = setOf(1L),
            selectedSystemIds = emptySet(),
            performanceFilter = PerformanceFilter.ALL,
        )
        assertEquals(0, count)
    }

    @Test
    fun getDifficultyCountsDelegatesToDatabaseProvider() = runTest {
        val expected: Map<DifficultyTier, Int> = mapOf(DifficultyTier.EASY to 5, DifficultyTier.DIFFICULT to 8)
        val db = FakeDatabaseProvider().apply {
            difficultyCountsProvider = { _, _, _, _, _ -> expected }
        }

        val result = useCase.getDifficultyCounts(
            db = db,
            selectedSubjectIds = setOf(1L),
            selectedSystemIds = emptySet(),
            performanceFilter = PerformanceFilter.ALL,
        )

        assertEquals(expected, result)
    }

    @Test
    fun getDifficultyCountsReturnsEmptyMapWhenDbIsNull() = runTest {
        val result = useCase.getDifficultyCounts(
            db = null,
            selectedSubjectIds = setOf(1L),
            selectedSystemIds = emptySet(),
            performanceFilter = PerformanceFilter.ALL,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun subjectsForSystemsFetchReturnsListOrNullWhenEmpty() {
        assertEquals(listOf(1L, 2L), useCase.subjectsForSystemsFetch(setOf(1L, 2L)))
        assertEquals(null, useCase.subjectsForSystemsFetch(emptySet()))
    }
}
