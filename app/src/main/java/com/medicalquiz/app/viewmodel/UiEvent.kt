package com.medicalquiz.app.viewmodel

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class OpenFilterDialog(val type: FilterDialogType) : UiEvent
    data class OpenMedia(val url: String) : UiEvent
    object OpenPerformanceDialog : UiEvent
    data class ShowErrorDialog(val title: String, val message: String) : UiEvent
    object ShowResetLogsConfirmation : UiEvent

    enum class FilterDialogType { SUBJECTS, SYSTEMS }
}
