package com.medicalquiz.app.viewmodel

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class OpenFilterDialog(val type: FilterDialogType) : UiEvent
    data class OpenMedia(val url: String) : UiEvent
    object OpenPerformanceDialog : UiEvent
    data class ShowAnswer(val correctAnswerId: Int, val selectedAnswerId: Int) : UiEvent

    enum class FilterDialogType { SUBJECTS, SYSTEMS }
}
