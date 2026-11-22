package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.LocalContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HtmlViewerViewModel(
    val fileName: String,
    private val localContentRepository: LocalContentRepository,
) : ViewModel() {

    private val _htmlDocument = MutableStateFlow<LocalContentRepository.HtmlDocumentResult?>(null)
    val htmlDocument: StateFlow<LocalContentRepository.HtmlDocumentResult?> = _htmlDocument.asStateFlow()

    init {
        viewModelScope.launch {
            _htmlDocument.value = localContentRepository.loadHtmlDocument(fileName)
        }
    }
}
