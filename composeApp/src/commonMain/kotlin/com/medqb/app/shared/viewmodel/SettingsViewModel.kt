package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.SettingsRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Inject
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val showMetadata: StateFlow<Boolean> = settingsRepository.showMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val fontScalePreference: StateFlow<Float?> = settingsRepository.fontScalePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setShowMetadata(enabled: Boolean) {
        settingsRepository.setShowMetadata(enabled)
    }

    fun setFontScalePreference(scale: Float?) {
        settingsRepository.setFontScalePreference(scale)
    }
}
