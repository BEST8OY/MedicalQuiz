package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import com.medqb.app.shared.data.SettingsRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow

@Inject
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val showMetadata: StateFlow<Boolean> = settingsRepository.showMetadata
    val fontScalePreference: StateFlow<Float?> = settingsRepository.fontScalePreference

    fun setShowMetadata(enabled: Boolean) {
        settingsRepository.setShowMetadata(enabled)
    }

    fun setFontScalePreference(scale: Float?) {
        settingsRepository.setFontScalePreference(scale)
    }
}
