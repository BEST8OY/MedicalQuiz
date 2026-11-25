package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.orchestration.MediaNavigationCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewerViewModel(
    private val mediaNavigationCoordinator: MediaNavigationCoordinator,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _mediaDescriptions = MutableStateFlow<Map<String, MediaDescription>>(emptyMap())
    val mediaDescriptions: StateFlow<Map<String, MediaDescription>> = _mediaDescriptions.asStateFlow()

    val fontScalePreference: StateFlow<Float> = settingsRepository.fontScalePreference
        .map { it ?: 1f }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.fontScalePreference.value ?: 1f,
        )

    init {
        viewModelScope.launch {
            _mediaDescriptions.value = mediaNavigationCoordinator.loadDescriptions()
        }
    }

    fun resolveMediaFilePath(fileName: String): String =
        mediaNavigationCoordinator.resolveMediaFilePath(fileName)

    suspend fun mediaFileExists(fileName: String): Boolean =
        mediaNavigationCoordinator.mediaFileExists(fileName)

    suspend fun resolveOverlayPaths(files: List<String>): Map<String, String?> =
        mediaNavigationCoordinator.resolveOverlayPaths(files)

    fun saveMedia(fileName: String) {
        mediaNavigationCoordinator.saveMediaFileAsync(fileName)
    }
}
