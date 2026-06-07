package com.medicalquiz.app.shared.domain

/**
 * App-level intents requested by feature ViewModels.
 *
 * These are navigation/workflow requests, not presentation-only messages. The
 * root App layer decides how each intent mutates Navigation 3 state or app
 * workflow state.
 */
sealed interface AppIntent {
    data class OpenMedia(val urls: List<String>, val startIndex: Int) : AppIntent
    data class OpenHtmlFile(val fileName: String) : AppIntent
    data object NavigateToDatabaseSelection : AppIntent
}

interface AppIntentSink {
    suspend fun send(intent: AppIntent)
}
