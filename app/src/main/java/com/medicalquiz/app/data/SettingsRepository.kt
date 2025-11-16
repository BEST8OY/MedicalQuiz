package com.medicalquiz.app.data

import android.content.Context
import android.content.SharedPreferences
import com.medicalquiz.app.data.database.PerformanceFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple settings repository that exposes flows for settings that affect ViewModel behavior.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLoggingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOGGING_ENABLED, true)
    )
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _performanceFilter = MutableStateFlow(
        PerformanceFilter.values().firstOrNull { it.storageValue == prefs.getString(KEY_PERFORMANCE_FILTER, PerformanceFilter.ALL.storageValue) }
            ?: PerformanceFilter.ALL
    )
    val performanceFilter: StateFlow<PerformanceFilter> = _performanceFilter.asStateFlow()

    fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply()
        _isLoggingEnabled.value = enabled
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        prefs.edit().putString(KEY_PERFORMANCE_FILTER, filter.storageValue).apply()
        _performanceFilter.value = filter
    }

    companion object {
        private const val PREFS_NAME = "medical_quiz_settings"
        private const val KEY_LOGGING_ENABLED = "log_answers_enabled"
        private const val KEY_PERFORMANCE_FILTER = "performance_filter"
    }
}
