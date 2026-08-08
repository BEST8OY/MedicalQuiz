package com.medqb.app.shared.domain

import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface SnackbarSink {
    suspend fun emitSnackbar(message: SnackbarMessage)
}

@Inject
@SingleIn(AppScope::class)
class SnackbarDispatcher : SnackbarSink {
    private val messageFlow = MutableSharedFlow<SnackbarMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<SnackbarMessage> = messageFlow.asSharedFlow()

    override suspend fun emitSnackbar(message: SnackbarMessage) {
        messageFlow.emit(message)
    }

    suspend fun emitSnackbar(message: String) {
        messageFlow.emit(SnackbarMessage.Simple(message))
    }
}
