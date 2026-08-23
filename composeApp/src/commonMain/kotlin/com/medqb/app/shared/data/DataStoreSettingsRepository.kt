package com.medqb.app.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

/**
 * [SettingsRepository] backed by Preferences DataStore — atomic writes and
 * corruption handling are provided by DataStore itself.
 */
@Inject
@SingleIn(AppScope::class)
class DataStoreSettingsRepository : SettingsRepository {
    // Process-scoped: intentionally not cancelled — survives config changes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { "${StorageProvider.getAppStorageDirectory()}/settings.preferences_pb".toPath() }
        )
    }

    override val showMetadata: StateFlow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.SHOW_METADATA] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    override val fontScalePreference: StateFlow<Float?> = dataStore.data
        .map { it[PreferenceKeys.FONT_SCALE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val isLoggingEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.LOGGING_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val submissionMode: StateFlow<SubmissionMode> = dataStore.data
        .map { prefs ->
            prefs[PreferenceKeys.SUBMISSION_MODE]
                ?.let { name -> runCatching { SubmissionMode.valueOf(name) }.getOrNull() }
                ?: SubmissionMode.INSTANT
        }
        .stateIn(scope, SharingStarted.Eagerly, SubmissionMode.INSTANT)

    /**
     * Fire-and-forget persistence, guarded: a failed write (e.g. disk full) must log
     * instead of crashing the process-scoped scope with an uncaught exception.
     */
    private fun editPreferences(transform: (MutablePreferences) -> Unit) {
        scope.launch {
            try {
                dataStore.edit(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("DataStoreSettingsRepository", "Error persisting setting", e)
            }
        }
    }

    override fun setShowMetadata(enabled: Boolean) =
        editPreferences { it[PreferenceKeys.SHOW_METADATA] = enabled }

    override fun setFontScalePreference(scale: Float?) = editPreferences { prefs ->
        if (scale == null) prefs.remove(PreferenceKeys.FONT_SCALE)
        else prefs[PreferenceKeys.FONT_SCALE] = scale
    }

    override fun setLoggingEnabled(enabled: Boolean) =
        editPreferences { it[PreferenceKeys.LOGGING_ENABLED] = enabled }

    override fun setSubmissionMode(mode: SubmissionMode) =
        editPreferences { it[PreferenceKeys.SUBMISSION_MODE] = mode.name }

    private object PreferenceKeys {
        val SHOW_METADATA = booleanPreferencesKey("show_metadata")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val SUBMISSION_MODE = stringPreferencesKey("submission_mode")
    }
}
