package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import com.medqb.app.shared.data.models.SubmissionMode
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
        val initialFilter = MedQBRoutes.Filter
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            initialFilter,
            MedQBRoutes.Quiz(sessionId = "s1"),
        )
        val navigator = AppNavigator(stack)
        assertEquals(3, stack.size)

        val popped = navigator.navigateBack()
        assertTrue(popped)
        assertEquals(2, stack.size)
        assertEquals(initialFilter, navigator.currentRoute)
        assertTrue(stack[1] === initialFilter)
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
    fun testNavigateToNonAdjacentExistingRoutePopsToExisting() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(sessionId = "session_1"),
            MedQBRoutes.HtmlViewer(fileName = "a.html"),
            MedQBRoutes.MediaViewer(files = listOf("image.png"), startIndex = 0),
        )
        val navigator = AppNavigator(stack)
        assertEquals(5, stack.size)

        // Navigating to existing HtmlViewer should pop MediaViewer
        navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = "a.html"))
        assertEquals(4, stack.size)
        assertEquals(MedQBRoutes.HtmlViewer(fileName = "a.html"), navigator.currentRoute)

        // Navigating to existing Filter should pop everything above Filter
        navigator.navigateTo(MedQBRoutes.Filter)
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
    fun testRoutesPolymorphicSerialization() {
        val json = Json {
            serializersModule = medQBSerializersModule
        }

        val routes: List<NavKey> = listOf(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter,
            MedQBRoutes.Quiz(
                sessionId = "123",
                entryName = "Test Quiz",
                initialQuestionIndex = 5,
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

        for (route in routes) {
            val encoded = json.encodeToString(kotlinx.serialization.PolymorphicSerializer(NavKey::class), route)
            val decoded = json.decodeFromString(kotlinx.serialization.PolymorphicSerializer(NavKey::class), encoded)
            assertEquals(route, decoded)
        }
    }
}
