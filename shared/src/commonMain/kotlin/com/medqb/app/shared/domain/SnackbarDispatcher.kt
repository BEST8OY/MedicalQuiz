package com.medqb.app.shared.domain

import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface SnackbarEvent {
    data class Show(val message: SnackbarMessage) : SnackbarEvent
    data object Dismiss : SnackbarEvent
}

interface SnackbarSink {
    suspend fun emitSnackbar(message: SnackbarMessage)
    fun tryDismiss() {}
}

@Inject
@SingleIn(AppScope::class)
class SnackbarDispatcher : SnackbarSink {
    private val eventFlow = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SnackbarEvent> = eventFlow.asSharedFlow()

    override suspend fun emitSnackbar(message: SnackbarMessage) {
        eventFlow.emit(SnackbarEvent.Show(message))
    }

    suspend fun emitSnackbar(message: String) {
        eventFlow.emit(SnackbarEvent.Show(SnackbarMessage.Simple(message)))
    }

    override fun tryDismiss() {
        eventFlow.tryEmit(SnackbarEvent.Dismiss)
    }
}
