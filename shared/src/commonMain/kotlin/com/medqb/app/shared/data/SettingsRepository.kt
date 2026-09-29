package com.medqb.app.shared.data

import com.medqb.app.shared.data.models.SubmissionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * App settings that affect ViewModel behavior. Implementations persist changes
 * and expose hot [StateFlow]s with sensible defaults before first load.
 */
interface SettingsRepository {
    val showMetadata: StateFlow<Boolean>
    val fontScalePreference: StateFlow<Float?>
    val isLoggingEnabled: StateFlow<Boolean>
    val submissionMode: StateFlow<SubmissionMode>

    fun setShowMetadata(enabled: Boolean)
    fun setFontScalePreference(scale: Float?)
    fun setLoggingEnabled(enabled: Boolean)
    fun setSubmissionMode(mode: SubmissionMode)
}
