package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
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

    private val settingsFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/settings.json"

    init {
        loadSettings()
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        saveSettings()
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        _performanceFilter.value = filter
    }

    private fun loadSettings() {
        try {
            val content = FileSystemHelper.readText(settingsFile)
            if (content != null) {
                // Simple manual JSON parsing
                if (content.contains("\"isLoggingEnabled\": false")) {
                    _isLoggingEnabled.value = false
                }
            }
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
        }
    }

    private fun saveSettings() {
        try {
            // Simple manual JSON generation
            val json = """
                {
                    "isLoggingEnabled": ${_isLoggingEnabled.value}
                }
            """.trimIndent()
            FileSystemHelper.writeText(settingsFile, json)
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
