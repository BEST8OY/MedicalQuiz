package com.medicalquiz.app.shared.platform

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.polymorphic

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MedicalQuizRoutes.DatabaseSelection::class, MedicalQuizRoutes.DatabaseSelection.serializer())
            subclass(MedicalQuizRoutes.Filter::class, MedicalQuizRoutes.Filter.serializer())
            subclass(MedicalQuizRoutes.Quiz::class, MedicalQuizRoutes.Quiz.serializer())
            subclass(MedicalQuizRoutes.Settings::class, MedicalQuizRoutes.Settings.serializer())
            subclass(MedicalQuizRoutes.MediaViewer::class, MedicalQuizRoutes.MediaViewer.serializer())
            subclass(MedicalQuizRoutes.HtmlViewer::class, MedicalQuizRoutes.HtmlViewer.serializer())
        }
    }
}

@Composable
actual fun rememberBackStack(startRoute: NavKey): NavBackStack<NavKey> {
    return rememberNavBackStack(navConfig, startRoute)
}
