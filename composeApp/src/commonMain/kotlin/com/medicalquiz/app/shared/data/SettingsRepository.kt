package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple settings repository that exposes flows for settings that affect ViewModel behavior.
 */
class SettingsRepository {
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _performanceFilter = MutableStateFlow(PerformanceFilter.ALL)
    val performanceFilter: StateFlow<PerformanceFilter> = _performanceFilter.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        _performanceFilter.value = filter
    }
}
