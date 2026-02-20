package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.viewmodel.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class UiEventDispatcher {
    private val eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = eventFlow.asSharedFlow()

    suspend fun emitToast(message: String) {
        eventFlow.emit(UiEvent.ShowToast(message))
    }

    suspend fun emitOpenMedia(urls: List<String>, startIndex: Int) {
        eventFlow.emit(UiEvent.OpenMedia(urls, startIndex))
    }

    suspend fun emitOpenHtml(fileName: String) {
        eventFlow.emit(UiEvent.OpenHtmlFile(fileName))
    }

    suspend fun emitNavigateToDatabaseSelection() {
        eventFlow.emit(UiEvent.NavigateToDatabaseSelection)
    }
}
