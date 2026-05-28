package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import com.medicalquiz.app.shared.data.SettingsRepository

/**
 * Scoped ViewModel for the Settings Screen.
 * Exposes setting states from the repository and routes settings changes.
 */
class SettingsViewModel(
    val settingsRepository: SettingsRepository
) : ViewModel() {
    // Standard ViewModel methods if needed. Settings screen interacts directly with settingsRepository
}
