package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.QuestionFilter
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared filter state holder scoped to the app graph.
 * Holds active query filter selections as a single atomic [QuestionFilter].
 */
@Inject
@SingleIn(AppScope::class)
class FilterStateHolder {
    private val lock = Any()

    private val _filter = MutableStateFlow(QuestionFilter.EMPTY)
    val filter: StateFlow<QuestionFilter> = _filter.asStateFlow()

    private val _selectedSubjectIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSubjectIds: StateFlow<Set<Long>> = _selectedSubjectIds.asStateFlow()

    private val _excludedSubjectIds = MutableStateFlow<Set<Long>>(emptySet())
    val excludedSubjectIds: StateFlow<Set<Long>> = _excludedSubjectIds.asStateFlow()

    private val _selectedSystemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSystemIds: StateFlow<Set<Long>> = _selectedSystemIds.asStateFlow()

    private val _excludedSystemIds = MutableStateFlow<Set<Long>>(emptySet())
    val excludedSystemIds: StateFlow<Set<Long>> = _excludedSystemIds.asStateFlow()

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter.asStateFlow()

    private val _selectedDifficultyTiers = MutableStateFlow<Set<DifficultyTier>>(emptySet())
    val selectedDifficultyTiers: StateFlow<Set<DifficultyTier>> = _selectedDifficultyTiers.asStateFlow()

    private fun updateUnderLock(transform: (QuestionFilter) -> QuestionFilter) {
        synchronized(lock) {
            val next = transform(_filter.value).sanitized()
            _filter.value = next
            _selectedSubjectIds.value = next.selectedSubjectIds
            _excludedSubjectIds.value = next.excludedSubjectIds
            _selectedSystemIds.value = next.selectedSystemIds
            _excludedSystemIds.value = next.excludedSystemIds
            _statusFilter.value = next.statusFilter
            _selectedDifficultyTiers.value = next.selectedDifficultyTiers
        }
    }

    fun updateSubjectIds(ids: Set<Long>) {
        updateUnderLock { it.copy(selectedSubjectIds = ids, excludedSubjectIds = it.excludedSubjectIds - ids) }
    }

    fun updateExcludedSubjectIds(ids: Set<Long>) {
        updateUnderLock { it.copy(excludedSubjectIds = ids, selectedSubjectIds = it.selectedSubjectIds - ids) }
    }

    fun setSubjects(included: Set<Long>, excluded: Set<Long>) {
        updateUnderLock { it.copy(selectedSubjectIds = included, excludedSubjectIds = excluded - included) }
    }

    fun updateSystemIds(ids: Set<Long>) {
        updateUnderLock { it.copy(selectedSystemIds = ids, excludedSystemIds = it.excludedSystemIds - ids) }
    }

    fun updateExcludedSystemIds(ids: Set<Long>) {
        updateUnderLock { it.copy(excludedSystemIds = ids, selectedSystemIds = it.selectedSystemIds - ids) }
    }

    fun setSystems(included: Set<Long>, excluded: Set<Long>) {
        updateUnderLock { it.copy(selectedSystemIds = included, excludedSystemIds = excluded - included) }
    }

    fun updateStatusFilter(filter: StatusFilter) {
        updateUnderLock { it.copy(statusFilter = filter) }
    }

    fun updateDifficultyTiers(tiers: Set<DifficultyTier>) {
        updateUnderLock { it.copy(selectedDifficultyTiers = tiers) }
    }

    fun updateFilter(filter: QuestionFilter) {
        updateUnderLock { filter }
    }

    fun reset() {
        updateUnderLock { QuestionFilter.EMPTY }
    }
}
