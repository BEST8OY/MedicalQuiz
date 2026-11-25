package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.medqb.app.shared.data.models.SubmissionMode
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {

    @Test
    fun testNavigateToAndNavigateBack() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(stack)

        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
        assertFalse(navigator.canNavigateBack)

        navigator.navigateTo(MedQBRoutes.Filter)
        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.Filter, navigator.currentRoute)
        assertTrue(navigator.canNavigateBack)

        val popped = navigator.navigateBack()
        assertTrue(popped)
        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
        assertFalse(navigator.canNavigateBack)

        val poppedRoot = navigator.navigateBack()
        assertFalse(poppedRoot)
        assertEquals(1, stack.size)
    }

    @Test
    fun testNavigateBackFromQuizPopsToFilter() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(sessionId = "s1"),
        )
        val navigator = AppNavigator(stack)
        assertEquals(3, stack.size)

        val popped = navigator.navigateBack()
        assertTrue(popped)
        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.Filter, navigator.currentRoute)
    }

    @Test
    fun testNavigateBackWhenQuizHasNoFilterUnderneathPopsToDatabaseSelection() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Quiz(sessionId = "s1"),
        )
        val navigator = AppNavigator(stack)
        assertEquals(2, stack.size)

        val popped = navigator.navigateBack()
        assertTrue(popped)
        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
    }

    @Test
    fun testOperationsOnEmptyStack() {
        val stack = mutableListOf<NavKey>()
        val navigator = AppNavigator(stack)
        assertFalse(navigator.canNavigateBack)
        assertFalse(navigator.navigateBack())
        navigator.popToDatabaseSelection()
        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
    }

    @Test
    fun testNavigateToDuplicateRouteIgnored() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(stack)

        navigator.navigateTo(MedQBRoutes.DatabaseSelection)
        assertEquals(1, stack.size)

        navigator.navigateTo(MedQBRoutes.Filter)
        assertEquals(2, stack.size)

        navigator.navigateTo(MedQBRoutes.Filter)
        assertEquals(2, stack.size)
    }

    @Test
    fun testNavigateOrPopToUnwindsExistingRoute() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(sessionId = "session_1"),
            MedQBRoutes.HtmlViewer(fileName = "a.html"),
            MedQBRoutes.MediaViewer(files = listOf("image.png"), startIndex = 0),
        )
        val navigator = AppNavigator(stack)
        assertEquals(5, stack.size)

        // Unwinding to existing HtmlViewer should pop MediaViewer
        navigator.navigateOrPopTo(MedQBRoutes.HtmlViewer(fileName = "a.html"))
        assertEquals(4, stack.size)
        assertEquals(MedQBRoutes.HtmlViewer(fileName = "a.html"), navigator.currentRoute)

        // Unwinding to existing Filter should pop everything above Filter
        navigator.navigateOrPopTo(MedQBRoutes.Filter)
        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.Filter, navigator.currentRoute)
    }

    @Test
    fun testPopToDatabaseSelectionWithDeepStack() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(sessionId = "session_1"),
            MedQBRoutes.MediaViewer(files = listOf("image.png"), startIndex = 0),
        )
        val navigator = AppNavigator(stack)
        assertEquals(4, stack.size)

        navigator.popToDatabaseSelection()

        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
    }

    @Test
    fun testPopToDatabaseSelectionWhenDatabaseSelectionMissingReinitializesRoot() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(sessionId = "session_orphaned"),
        )
        val navigator = AppNavigator(stack)
        assertEquals(2, stack.size)

        navigator.popToDatabaseSelection()

        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
    }

    @Test
    fun testPopToDatabaseSelectionWhenAlreadyAtRoot() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(stack)

        navigator.popToDatabaseSelection()

        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)
    }

    @Test
    fun testRoutesPolymorphicAndSavedStateSerialization() {
        val json = Json {
            serializersModule = medQBSerializersModule
        }

        val routes: List<MedQBRoutes> = listOf(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(
                sessionId = "123",
                databaseName = "cardiology",
                entryName = "Test Quiz",
                initialQuestionIndex = 5,
                initialQuestionId = 42L,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.MANUAL,
            ),
            MedQBRoutes.Quiz(
                sessionId = "456",
                entryName = "Instant Quiz",
                initialQuestionIndex = 0,
                isLoggingEnabled = false,
                submissionMode = SubmissionMode.INSTANT,
            ),
            MedQBRoutes.Quiz(
                sessionId = "789",
                entryName = "Default Mode Quiz",
                initialQuestionIndex = 0,
                isLoggingEnabled = null,
                submissionMode = null,
            ),
            MedQBRoutes.Settings,
            MedQBRoutes.MediaViewer(files = listOf("img1.png", "img2.png"), startIndex = 1),
            MedQBRoutes.HtmlViewer(fileName = "doc.html"),
        )

        // Exhaustive check ensuring every MedQBRoutes subtype has at least one test case.
        val coveredVariants = routes.map { route ->
            when (route) {
                is MedQBRoutes.DatabaseSelection -> "DatabaseSelection"
                is MedQBRoutes.Filter -> "Filter"
                is MedQBRoutes.Quiz -> "Quiz"
                is MedQBRoutes.Settings -> "Settings"
                is MedQBRoutes.MediaViewer -> "MediaViewer"
                is MedQBRoutes.HtmlViewer -> "HtmlViewer"
            }
        }.toSet()
        assertEquals(6, coveredVariants.size)

        val navKeySerializer = PolymorphicSerializer(NavKey::class)
        for (route in routes) {
            val encodedJson = json.encodeToString(navKeySerializer, route)
            val decodedJson = json.decodeFromString(navKeySerializer, encodedJson)
            assertEquals(route, decodedJson)

            val savedState = encodeToSavedState(navKeySerializer, route, MedQBNavConfig)
            val decodedSavedState = decodeFromSavedState(navKeySerializer, savedState, MedQBNavConfig)
            assertEquals(route, decodedSavedState)
        }
    }
}
