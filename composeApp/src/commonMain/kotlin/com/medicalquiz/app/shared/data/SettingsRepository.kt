package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Simple settings repository that exposes flows for settings that affect ViewModel behavior.
 */
class SettingsRepository {
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _showMetadata = MutableStateFlow(true)
    val showMetadata: StateFlow<Boolean> = _showMetadata.asStateFlow()

    private val _fontSize = MutableStateFlow(16f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val settingsFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/settings.json"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        loadSettings()
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        saveSettings()
    }

    fun setShowMetadata(enabled: Boolean) {
        _showMetadata.value = enabled
        saveSettings()
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        _performanceFilter.value = filter
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        saveSettings()
    }

    private fun loadSettings() {
        try {
            val content = FileSystemHelper.readText(settingsFile)
            if (content != null) {
                val payload = json.decodeFromString(SettingsPayload.serializer(), content)
                _isLoggingEnabled.value = payload.isLoggingEnabled
                _showMetadata.value = payload.showMetadata
                _fontSize.value = payload.fontSize
            }
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
        }
    }

    private fun saveSettings() {
        try {
            val payload = SettingsPayload(
                isLoggingEnabled = _isLoggingEnabled.value,
                showMetadata = _showMetadata.value,
                fontSize = _fontSize.value
            )
            val jsonString = json.encodeToString(payload)
            FileSystemHelper.writeText(settingsFile, jsonString)
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }

    @Serializable
    private data class SettingsPayload(
        val isLoggingEnabled: Boolean = true,
        val showMetadata: Boolean = true,
        val fontSize: Float = 16f
    )
}
