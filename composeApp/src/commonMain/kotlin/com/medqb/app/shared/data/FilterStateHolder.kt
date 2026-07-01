package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.ui.screens.FilterPane
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Shared filter state holder scoped to the app graph.
 * Eliminates duplication of filter selections between FilterViewModel and QuizViewModel.
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
        _pendingHistoryEntryId.value = null
        _pendingHistoryQuestionIndex.value = 0
        _pendingFilterPane.value = null
    }

    private val _pendingHistoryEntryId = MutableStateFlow<String?>(null)
    val pendingHistoryEntryId: StateFlow<String?> = _pendingHistoryEntryId.asStateFlow()

    fun setPendingHistoryEntryId(id: String?) {
        _pendingHistoryEntryId.value = id
    }

    fun consumePendingHistoryEntryId(): String? {
        return _pendingHistoryEntryId.getAndUpdate { null }
    }

    private val _pendingHistoryQuestionIndex = MutableStateFlow(0)
    val pendingHistoryQuestionIndex: StateFlow<Int> = _pendingHistoryQuestionIndex.asStateFlow()

    fun setPendingHistoryQuestionIndex(index: Int) {
        _pendingHistoryQuestionIndex.value = index
    }

    fun consumePendingHistoryQuestionIndex(): Int {
        return _pendingHistoryQuestionIndex.getAndUpdate { 0 }
    }

    private val _pendingFilterPane = MutableStateFlow<FilterPane?>(null)
    val pendingFilterPane: StateFlow<FilterPane?> = _pendingFilterPane.asStateFlow()

    fun setPendingFilterPane(pane: FilterPane?) {
        _pendingFilterPane.value = pane
    }

    fun consumePendingFilterPane(): FilterPane? {
        return _pendingFilterPane.getAndUpdate { null }
    }
}
