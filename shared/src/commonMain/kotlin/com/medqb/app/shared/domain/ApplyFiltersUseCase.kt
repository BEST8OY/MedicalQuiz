package com.medqb.app.shared.domain

import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.QuestionFilter
import dev.zacsweers.metro.Inject

@Inject
class ApplyFiltersUseCase {

    suspend fun pruneSystemsForSubjects(
        db: DatabaseProvider?,
        newSubjectIds: Set<Long>,
        selectedSystems: Set<Long>,
        excludedSystems: Set<Long> = emptySet(),
    ): Pair<Set<Long>, Set<Long>> {
        if (newSubjectIds.isEmpty()) return selectedSystems to excludedSystems

        val validSystems = db?.getSystems(newSubjectIds.toList())
            ?.map { it.id }
            ?.toSet()
            ?: emptySet()

        return selectedSystems.intersect(validSystems) to excludedSystems.intersect(validSystems)
    }

    suspend fun pruneSystemsForSubjects(
        db: DatabaseProvider?,
        newSubjectIds: Set<Long>,
        previouslySelectedSystems: Set<Long>,
    ): Set<Long> = pruneSystemsForSubjects(
        db = db,
        newSubjectIds = newSubjectIds,
        selectedSystems = previouslySelectedSystems,
        excludedSystems = emptySet(),
    ).first

    suspend fun normalizeSelectedSystems(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        selectedSystems: Set<Long>,
        excludedSystems: Set<Long> = emptySet(),
    ): Pair<Set<Long>, Set<Long>> {
        val availableSystems = availableSystems(
            db = db,
            selectedSubjectIds = selectedSubjectIds,
        )
        return if (availableSystems.isEmpty()) {
            emptySet<Long>() to emptySet<Long>()
        } else {
            selectedSystems.intersect(availableSystems) to excludedSystems.intersect(availableSystems)
        }
    }

    suspend fun normalizeSelectedSystems(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        newSystemIds: Set<Long>,
    ): Set<Long> = normalizeSelectedSystems(
        db = db,
        selectedSubjectIds = selectedSubjectIds,
        selectedSystems = newSystemIds,
        excludedSystems = emptySet(),
    ).first

    suspend fun previewQuestionCount(
        db: DatabaseProvider?,
        filter: QuestionFilter,
    ): Int = previewQuestionCount(
        db = db,
        selectedSubjectIds = filter.selectedSubjectIds,
        excludedSubjectIds = filter.excludedSubjectIds,
        selectedSystemIds = filter.selectedSystemIds,
        excludedSystemIds = filter.excludedSystemIds,
        statusFilter = filter.statusFilter,
        difficultyFilters = filter.selectedDifficultyTiers,
    )

    suspend fun previewQuestionCount(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long> = emptySet(),
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long> = emptySet(),
        statusFilter: StatusFilter,
        difficultyFilters: Set<DifficultyTier> = emptySet(),
    ): Int {
        return db?.countQuestionIds(
            subjectIds = selectedSubjectIds.toIdListOrNull(),
            excludedSubjectIds = excludedSubjectIds.toIdListOrNull(),
            systemIds = selectedSystemIds.toIdListOrNull(),
            excludedSystemIds = excludedSystemIds.toIdListOrNull(),
            statusFilter = statusFilter,
            difficultyFilters = difficultyFilters,
        ) ?: 0
    }

    suspend fun getDifficultyCounts(
        db: DatabaseProvider?,
        filter: QuestionFilter,
    ): Map<DifficultyTier, Int> = getDifficultyCounts(
        db = db,
        selectedSubjectIds = filter.selectedSubjectIds,
        excludedSubjectIds = filter.excludedSubjectIds,
        selectedSystemIds = filter.selectedSystemIds,
        excludedSystemIds = filter.excludedSystemIds,
        statusFilter = filter.statusFilter,
    )

    suspend fun getDifficultyCounts(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long> = emptySet(),
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long> = emptySet(),
        statusFilter: StatusFilter,
    ): Map<DifficultyTier, Int> {
        return db?.getDifficultyCounts(
            subjectIds = selectedSubjectIds.toIdListOrNull(),
            excludedSubjectIds = excludedSubjectIds.toIdListOrNull(),
            systemIds = selectedSystemIds.toIdListOrNull(),
            excludedSystemIds = excludedSystemIds.toIdListOrNull(),
            statusFilter = statusFilter,
        ) ?: emptyMap()
    }

    private fun Set<Long>.toIdListOrNull(): List<Long>? =
        takeIf { it.isNotEmpty() }?.toList()

    private suspend fun availableSystems(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
    ): Set<Long> {
        val provider = db ?: return emptySet()
        val systems = if (selectedSubjectIds.isEmpty()) {
            provider.getSystems(null)
        } else {
            provider.getSystems(selectedSubjectIds.toList())
        }
        return systems.map { it.id }.toSet()
    }
}
