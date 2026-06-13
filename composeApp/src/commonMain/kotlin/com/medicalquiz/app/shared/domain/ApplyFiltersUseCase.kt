package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.LogsProvider
import com.medicalquiz.app.shared.data.database.PerformanceFilter

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
        logsProvider: LogsProvider?,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
    ): Int {
        val allIds = db?.getQuestionIds(
            subjectIds = selectedSubjectIds.toList(),
            systemIds = selectedSystemIds.toList(),
        ) ?: return 0

        if (performanceFilter == PerformanceFilter.ALL || logsProvider == null) {
            return allIds.size
        }

        val filtered = logsProvider.getQuestionIdsByPerformance(allIds, performanceFilter)
        return filtered.size
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
        return systems?.map { it.id }?.toSet() ?: emptySet()
    }
}
