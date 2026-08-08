package com.medqb.app.shared.data

import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Simple settings repository that exposes flows for settings that affect ViewModel behavior.
 */
@Inject
@SingleIn(AppScope::class)
class SettingsRepository {
    // Process-scoped: intentionally not cancelled — survives config changes
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()

    private val _showMetadata = MutableStateFlow(true)
    val showMetadata: StateFlow<Boolean> = _showMetadata.asStateFlow()

    private val _fontScalePreference = MutableStateFlow<Float?>(null)
    val fontScalePreference: StateFlow<Float?> = _fontScalePreference.asStateFlow()

    private val _isLoggingEnabled = MutableStateFlow(false)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _submissionMode = MutableStateFlow(SubmissionMode.INSTANT)
    val submissionMode: StateFlow<SubmissionMode> = _submissionMode.asStateFlow()

    private val settingsFile: String
        get() = "${StorageProvider.getAppStorageDirectory()}/settings.json"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        ioScope.launch { refreshSettingsAsync() }
    }

    /**
     * Acquire the file lock while writing the in-memory StateFlow so a concurrent
     * [refreshSettingsAsync] cannot overwrite a fresh setter value with stale file data.
     * Without this, the race is:
     *   1. refresh reads the file under the mutex
     *   2. setter writes _showMetadata.value = true here
     *   3. refresh's loadSettingsInternal() writes _showMetadata.value = staleFileValue
     */
    fun setShowMetadata(enabled: Boolean) {
        ioScope.launch {
            fileMutex.withLock { _showMetadata.value = enabled }
            saveSettingsAsync()
        }
    }

    fun setFontScalePreference(scale: Float?) {
        ioScope.launch {
            fileMutex.withLock { _fontScalePreference.value = scale }
            saveSettingsAsync()
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        ioScope.launch {
            fileMutex.withLock { _isLoggingEnabled.value = enabled }
            saveSettingsAsync()
        }
    }

    fun setSubmissionMode(mode: SubmissionMode) {
        ioScope.launch {
            fileMutex.withLock { _submissionMode.value = mode }
            saveSettingsAsync()
        }
    }

    suspend fun refreshSettingsAsync(): SettingsSnapshot = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            loadSettingsInternal()
            SettingsSnapshot(
                showMetadata = _showMetadata.value,
                fontScalePreference = _fontScalePreference.value,
                isLoggingEnabled = _isLoggingEnabled.value,
                submissionMode = _submissionMode.value,
            )
        }
    }

    suspend fun saveSettingsAsync() = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            saveSettingsInternal()
        }
    }

    private fun loadSettingsInternal() {
        try {
            val content = FileSystemHelper.readText(settingsFile)
            if (content != null) {
                val payload = json.decodeFromString(SettingsPayload.serializer(), content)
                _showMetadata.value = payload.showMetadata
                _fontScalePreference.value = payload.fontScalePreference
                    ?: payload.fontSize?.toLegacyScalePreference()
                _isLoggingEnabled.value = payload.isLoggingEnabled
                payload.submissionMode?.let { name ->
                    runCatching { SubmissionMode.valueOf(name) }
                        .onSuccess { _submissionMode.value = it }
                }
            }
        } catch (e: Exception) {
            Logger.e("SettingsRepository", "Error loading settings", e)
        }
    }

    private fun saveSettingsInternal() {
        try {
            val payload = SettingsPayload(
                showMetadata = _showMetadata.value,
                fontScalePreference = _fontScalePreference.value,
                isLoggingEnabled = _isLoggingEnabled.value,
                submissionMode = _submissionMode.value.name,
            )
            val jsonString = json.encodeToString(payload)
            FileSystemHelper.writeText(settingsFile, jsonString)
        } catch (e: Exception) {
            Logger.e("SettingsRepository", "Error saving settings", e)
        }
    }

    @Serializable
    private data class SettingsPayload(
        val showMetadata: Boolean = true,
        val fontScalePreference: Float? = null,
        // Legacy setting kept for migration when reading older settings files.
        val fontSize: Float? = null,
        val isLoggingEnabled: Boolean = false,
        val submissionMode: String? = null,
    )

    private fun Float.toLegacyScalePreference(): Float =
        FontScalePresets.nearestTo(this / LEGACY_BASE_FONT_SIZE)

    private companion object {
        const val LEGACY_BASE_FONT_SIZE = 16f
    }

    data class SettingsSnapshot(
        val showMetadata: Boolean,
        val fontScalePreference: Float?,
        val isLoggingEnabled: Boolean = false,
        val submissionMode: SubmissionMode = SubmissionMode.INSTANT,
    )
}
