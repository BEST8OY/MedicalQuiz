package com.medqb.app.shared.domain

import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import dev.zacsweers.metro.Inject

@Inject
class ApplyFiltersUseCase {

    suspend fun pruneSystemsForSubjects(
        db: DatabaseProvider?,
        newSubjectIds: Set<Long>,
        previouslySelectedSystems: Set<Long>,
    ): Set<Long> {
        if (newSubjectIds.isEmpty()) return previouslySelectedSystems

        val validSystems = db?.getSystems(newSubjectIds.toList())
            ?.map { it.id }
            ?.toSet()
            ?: emptySet()

        return previouslySelectedSystems.intersect(validSystems)
    }

    suspend fun normalizeSelectedSystems(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        newSystemIds: Set<Long>,
    ): Set<Long> {
        val availableSystems = availableSystems(
            db = db,
            selectedSubjectIds = selectedSubjectIds,
        )
        return if (availableSystems.isEmpty()) {
            emptySet()
        } else {
            newSystemIds.intersect(availableSystems)
        }
    }

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
            subjectIds = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList(),
            excludedSubjectIds = excludedSubjectIds.takeIf { it.isNotEmpty() }?.toList(),
            systemIds = selectedSystemIds.takeIf { it.isNotEmpty() }?.toList(),
            excludedSystemIds = excludedSystemIds.takeIf { it.isNotEmpty() }?.toList(),
            statusFilter = statusFilter,
            difficultyFilters = difficultyFilters,
        ) ?: 0
    }

    suspend fun getDifficultyCounts(
        db: DatabaseProvider?,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long> = emptySet(),
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long> = emptySet(),
        statusFilter: StatusFilter,
    ): Map<DifficultyTier, Int> {
        return db?.getDifficultyCounts(
            subjectIds = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList(),
            excludedSubjectIds = excludedSubjectIds.takeIf { it.isNotEmpty() }?.toList(),
            systemIds = selectedSystemIds.takeIf { it.isNotEmpty() }?.toList(),
            excludedSystemIds = excludedSystemIds.takeIf { it.isNotEmpty() }?.toList(),
            statusFilter = statusFilter,
        ) ?: emptyMap()
    }

    fun subjectsForSystemsFetch(subjectIds: Set<Long>): List<Long>? =
        subjectIds.takeIf { it.isNotEmpty() }?.toList()

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
