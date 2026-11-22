package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.medqb.app.shared.data.models.SubmissionMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Navigation routes for MedQB application using Navigation 3.
 *
 * All routes implement [NavKey] and are registered in SavedStateConfiguration
 * for state restoration across process recreation.
 */
@Serializable
sealed interface MedQBRoutes : NavKey {

    @Serializable
    data object DatabaseSelection : MedQBRoutes

    @Serializable
    data object Filter : MedQBRoutes

    @Serializable
    data class Quiz(
        val sessionId: String = "",
        val entryName: String = "",
        val initialQuestionIndex: Int = 0,
        val isLoggingEnabled: Boolean? = null,
        val submissionMode: SubmissionMode? = null,
    ) : MedQBRoutes

    @Serializable
    data object Settings : MedQBRoutes

    @Serializable
    data class MediaViewer(
        val files: List<String>,
        val startIndex: Int = 0,
    ) : MedQBRoutes

    @Serializable
    data class HtmlViewer(
        val fileName: String,
    ) : MedQBRoutes
}

val medQBSerializersModule: SerializersModule = SerializersModule {
    polymorphic(baseClass = NavKey::class) {
        subclass(serializer = MedQBRoutes.DatabaseSelection.serializer())
        subclass(serializer = MedQBRoutes.Filter.serializer())
        subclass(serializer = MedQBRoutes.Quiz.serializer())
        subclass(serializer = MedQBRoutes.Settings.serializer())
        subclass(serializer = MedQBRoutes.MediaViewer.serializer())
        subclass(serializer = MedQBRoutes.HtmlViewer.serializer())
    }
}

val MedQBNavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = medQBSerializersModule
}
