package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared filter state holder scoped to the app graph.
 * Holds active query filter selections (subjects, systems, performance filter).
 */
@Inject
@SingleIn(AppScope::class)
class FilterStateHolder {
    private val _selectedSubjectIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSubjectIds: StateFlow<Set<Long>> = _selectedSubjectIds.asStateFlow()

    private val _selectedSystemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSystemIds: StateFlow<Set<Long>> = _selectedSystemIds.asStateFlow()

    private val _performanceFilter = MutableStateFlow(PerformanceFilter.ALL)
    val performanceFilter: StateFlow<PerformanceFilter> = _performanceFilter.asStateFlow()

    fun updateSubjectIds(ids: Set<Long>) {
        _selectedSubjectIds.value = ids
    }

    fun updateSystemIds(ids: Set<Long>) {
        _selectedSystemIds.value = ids
    }

    fun updatePerformanceFilter(filter: PerformanceFilter) {
        _performanceFilter.value = filter
    }

    fun reset() {
        _selectedSubjectIds.value = emptySet()
        _selectedSystemIds.value = emptySet()
        _performanceFilter.value = PerformanceFilter.ALL
    }
}
