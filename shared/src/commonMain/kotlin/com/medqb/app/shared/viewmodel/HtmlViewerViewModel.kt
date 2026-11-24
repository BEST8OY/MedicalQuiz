package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HtmlViewerViewModel(
    val fileName: String,
    private val localContentRepository: LocalContentRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _htmlDocument = MutableStateFlow<LocalContentRepository.HtmlDocumentResult?>(null)
    val htmlDocument: StateFlow<LocalContentRepository.HtmlDocumentResult?> = _htmlDocument.asStateFlow()

    val fontScalePreference: StateFlow<Float> = settingsRepository.fontScalePreference
        .map { it ?: 1f }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.fontScalePreference.value ?: 1f,
        )

    init {
        viewModelScope.launch {
            _htmlDocument.value = localContentRepository.loadHtmlDocument(fileName)
        }
    }
}
