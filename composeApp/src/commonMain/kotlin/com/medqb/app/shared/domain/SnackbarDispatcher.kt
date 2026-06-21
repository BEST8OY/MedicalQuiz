package com.medqb.app.shared.domain

import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface SnackbarSink {
    suspend fun emitSnackbar(message: String)
}

@Inject
@SingleIn(AppScope::class)
class SnackbarDispatcher : SnackbarSink {
    private val messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = messageFlow.asSharedFlow()

    override suspend fun emitSnackbar(message: String) {
        messageFlow.emit(message)
    }
}
