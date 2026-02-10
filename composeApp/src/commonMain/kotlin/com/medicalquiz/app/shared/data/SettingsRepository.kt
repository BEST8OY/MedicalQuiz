package com.medicalquiz.app.shared.data

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

    private val _fontScalePreference = MutableStateFlow<Float?>(null)
    val fontScalePreference: StateFlow<Float?> = _fontScalePreference.asStateFlow()

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

    fun setFontScalePreference(scale: Float?) {
        _fontScalePreference.value = scale
        saveSettings()
    }

    private fun loadSettings() {
        try {
            val content = FileSystemHelper.readText(settingsFile)
            if (content != null) {
                val payload = json.decodeFromString(SettingsPayload.serializer(), content)
                _isLoggingEnabled.value = payload.isLoggingEnabled
                _showMetadata.value = payload.showMetadata
                _fontScalePreference.value = payload.fontScalePreference
                    ?: payload.fontSize?.toLegacyScalePreference()
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
                fontScalePreference = _fontScalePreference.value,
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
        val fontScalePreference: Float? = null,
        // Legacy setting kept for migration when reading older settings files.
        val fontSize: Float? = null,
    )

    private fun Float.toLegacyScalePreference(): Float =
        FontScalePresets.nearestTo(this / LEGACY_BASE_FONT_SIZE)

    private companion object {
        const val LEGACY_BASE_FONT_SIZE = 16f
    }
}
