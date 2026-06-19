package com.medqb.app.shared.domain

import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.PerformanceFilter

class ApplyFiltersUseCase {

    suspend fun pruneSystemsForSubjects(
        db: DatabaseProvider?,
        newSubjectIds: Set<Long>,
        previouslySelectedSystems: Set<Long>,
    ): Set<Long> {
        val validSystems = if (newSubjectIds.isEmpty()) {
            emptySet()
        } else {
            db?.getSystems(newSubjectIds.toList())
                ?.map { it.id }
                ?.toSet()
                ?: emptySet()
        }

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
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
    ): Int {
        return db?.getQuestionIds(
            subjectIds = selectedSubjectIds.toList(),
            systemIds = selectedSystemIds.toList(),
            performanceFilter = performanceFilter,
        )?.size ?: 0
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
