package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import com.medicalquiz.app.shared.data.SettingsRepository

import kotlinx.coroutines.flow.StateFlow

/**
 * Scoped ViewModel for the Settings Screen.
 * Exposes setting states from the repository and routes settings changes.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val showMetadata: StateFlow<Boolean> = settingsRepository.showMetadata
    val fontScalePreference: StateFlow<Float?> = settingsRepository.fontScalePreference

    fun setShowMetadata(visible: Boolean) {
        settingsRepository.setShowMetadata(visible)
    }

    fun setFontScalePreference(scale: Float?) {
        settingsRepository.setFontScalePreference(scale)
    }
}
