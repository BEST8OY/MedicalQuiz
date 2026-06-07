package com.medicalquiz.app.shared.viewmodel

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class OpenMedia(val urls: List<String>, val startIndex: Int) : UiEvent
    data class OpenHtmlFile(val fileName: String) : UiEvent
    data object NavigateToDatabaseSelection : UiEvent
}
