package com.medqb.app.shared.data.models

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter

/**
 * Value object representing all active question filter criteria.
 * Use [sanitized] or [create] to ensure disjointness between selected and excluded IDs.
 */
data class QuestionFilter(
    val selectedSubjectIds: Set<Long> = emptySet(),
    val excludedSubjectIds: Set<Long> = emptySet(),
    val selectedSystemIds: Set<Long> = emptySet(),
    val excludedSystemIds: Set<Long> = emptySet(),
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
) {
    val isDefault: Boolean
        get() = selectedSubjectIds.isEmpty() &&
                excludedSubjectIds.isEmpty() &&
                selectedSystemIds.isEmpty() &&
                excludedSystemIds.isEmpty() &&
                statusFilter.isAll &&
                (selectedDifficultyTiers.isEmpty() || selectedDifficultyTiers.size == DifficultyTier.entries.size)

    /**
     * Returns a copy with excluded IDs strictly disjoint from selected IDs.
     */
    fun sanitized(): QuestionFilter = copy(
        excludedSubjectIds = excludedSubjectIds - selectedSubjectIds,
        excludedSystemIds = excludedSystemIds - selectedSystemIds,
    )

    companion object {
        val EMPTY = QuestionFilter()

        fun create(
            selectedSubjectIds: Set<Long> = emptySet(),
            excludedSubjectIds: Set<Long> = emptySet(),
            selectedSystemIds: Set<Long> = emptySet(),
            excludedSystemIds: Set<Long> = emptySet(),
            statusFilter: StatusFilter = StatusFilter.ALL,
            selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
        ): QuestionFilter = QuestionFilter(
            selectedSubjectIds = selectedSubjectIds,
            excludedSubjectIds = excludedSubjectIds - selectedSubjectIds,
            selectedSystemIds = selectedSystemIds,
            excludedSystemIds = excludedSystemIds - selectedSystemIds,
            statusFilter = statusFilter,
            selectedDifficultyTiers = selectedDifficultyTiers,
        )
    }
}
