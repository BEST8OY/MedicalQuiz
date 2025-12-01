package com.medicalquiz.app.shared.viewmodel

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class OpenMedia(val urls: List<String>, val startIndex: Int) : UiEvent
    data object OpenPerformanceDialog : UiEvent
    data class ShowErrorDialog(val title: String, val message: String) : UiEvent
    data object ShowResetLogsConfirmation : UiEvent
}
