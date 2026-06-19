package com.medqb.app.shared.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppIntentDispatcher : AppIntentSink {
    private val intentFlow = MutableSharedFlow<AppIntent>(extraBufferCapacity = 4)
    val intents: SharedFlow<AppIntent> = intentFlow.asSharedFlow()

    override suspend fun send(intent: AppIntent) {
        intentFlow.emit(intent)
    }

    suspend fun emitOpenMedia(urls: List<String>, startIndex: Int) {
        send(AppIntent.OpenMedia(urls, startIndex))
    }

    suspend fun emitOpenHtml(fileName: String) {
        send(AppIntent.OpenHtmlFile(fileName))
    }

    suspend fun emitNavigateToDatabaseSelection() {
        send(AppIntent.NavigateToDatabaseSelection)
    }
}
