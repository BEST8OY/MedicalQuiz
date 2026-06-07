package com.medicalquiz.app.shared.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface SnackbarSink {
    suspend fun emitSnackbar(message: String)
}

class SnackbarDispatcher : SnackbarSink {
    private val messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = messageFlow.asSharedFlow()

    override suspend fun emitSnackbar(message: String) {
        messageFlow.emit(message)
    }
}
