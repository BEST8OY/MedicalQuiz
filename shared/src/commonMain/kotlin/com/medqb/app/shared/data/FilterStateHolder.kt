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
import kotlinx.coroutines.flow.update

/**
 * Shared filter state holder scoped to the app graph.
 * Holds active query filter selections as a single atomic [QuestionFilter].
 */
@Inject
@SingleIn(AppScope::class)
class FilterStateHolder {
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

    private fun updateFilterInternal(transform: (QuestionFilter) -> QuestionFilter) {
        _filter.update { transform(it).sanitized() }
        val latest = _filter.value
        _selectedSubjectIds.value = latest.selectedSubjectIds
        _excludedSubjectIds.value = latest.excludedSubjectIds
        _selectedSystemIds.value = latest.selectedSystemIds
        _excludedSystemIds.value = latest.excludedSystemIds
        _statusFilter.value = latest.statusFilter
        _selectedDifficultyTiers.value = latest.selectedDifficultyTiers
    }

    fun updateSubjectIds(ids: Set<Long>) {
        updateFilterInternal { it.copy(selectedSubjectIds = ids, excludedSubjectIds = it.excludedSubjectIds - ids) }
    }

    fun updateExcludedSubjectIds(ids: Set<Long>) {
        updateFilterInternal { it.copy(excludedSubjectIds = ids, selectedSubjectIds = it.selectedSubjectIds - ids) }
    }

    fun setSubjects(included: Set<Long>, excluded: Set<Long>) {
        updateFilterInternal { it.copy(selectedSubjectIds = included, excludedSubjectIds = excluded - included) }
    }

    fun updateSystemIds(ids: Set<Long>) {
        updateFilterInternal { it.copy(selectedSystemIds = ids, excludedSystemIds = it.excludedSystemIds - ids) }
    }

    fun updateExcludedSystemIds(ids: Set<Long>) {
        updateFilterInternal { it.copy(excludedSystemIds = ids, selectedSystemIds = it.selectedSystemIds - ids) }
    }

    fun setSystems(included: Set<Long>, excluded: Set<Long>) {
        updateFilterInternal { it.copy(selectedSystemIds = included, excludedSystemIds = excluded - included) }
    }

    fun setSubjectsAndSystems(
        selectedSubjects: Set<Long>,
        excludedSubjects: Set<Long>,
        selectedSystems: Set<Long>,
        excludedSystems: Set<Long>,
    ) {
        updateFilterInternal {
            it.copy(
                selectedSubjectIds = selectedSubjects,
                excludedSubjectIds = excludedSubjects - selectedSubjects,
                selectedSystemIds = selectedSystems,
                excludedSystemIds = excludedSystems - selectedSystems,
            )
        }
    }

    fun updateStatusFilter(filter: StatusFilter) {
        updateFilterInternal { it.copy(statusFilter = filter) }
    }

    fun updateDifficultyTiers(tiers: Set<DifficultyTier>) {
        updateFilterInternal { it.copy(selectedDifficultyTiers = tiers) }
    }

    fun updateFilter(filter: QuestionFilter) {
        updateFilterInternal { filter }
    }

    fun reset() {
        updateFilterInternal { QuestionFilter.EMPTY }
    }
}
