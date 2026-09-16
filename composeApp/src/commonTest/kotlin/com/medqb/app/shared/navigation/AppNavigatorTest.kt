package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

        navigator.navigateTo(MedQBRoutes.Filter())
        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.Filter(), navigator.currentRoute)
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
    fun testNavigateToDuplicateRouteIgnored() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(stack)

        navigator.navigateTo(MedQBRoutes.DatabaseSelection)
        assertEquals(1, stack.size)

        navigator.navigateTo(MedQBRoutes.Filter())
        assertEquals(2, stack.size)

        navigator.navigateTo(MedQBRoutes.Filter())
        assertEquals(2, stack.size)
    }

    @Test
    fun testPopToDatabaseSelectionWithDeepStack() {
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter(),
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
    fun testReturnQuizToFilterPreservesExistingFilterKey() {
        val initialFilter = MedQBRoutes.Filter(initialPaneName = null)
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection, initialFilter)
        val navigator = AppNavigator(stack)

        navigator.navigateTo(MedQBRoutes.Quiz(sessionId = "session_1"))
        assertEquals(3, stack.size)

        // Return quiz to filter requesting "History" pane
        navigator.returnQuizToFilter("History")

        // Stack size should be 2, and the Filter entry at index 1 must be the exact original object instance
        assertEquals(2, stack.size)
        assertEquals(initialFilter, stack[1])
        assertTrue(stack[1] === initialFilter, "Existing Filter instance identity should be preserved without mutating NavKey in-place")
    }

    @Test
    fun testReturnQuizToFilterWithDeepStack() {
        val initialFilter = MedQBRoutes.Filter()
        val stack = mutableListOf<NavKey>(
            MedQBRoutes.DatabaseSelection,
            initialFilter,
            MedQBRoutes.Quiz(sessionId = "session_1"),
            MedQBRoutes.MediaViewer(files = listOf("diag.jpg"), startIndex = 0),
            MedQBRoutes.HtmlViewer(fileName = "details.html"),
        )
        val navigator = AppNavigator(stack)
        assertEquals(5, stack.size)

        navigator.returnQuizToFilter()

        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, stack[0])
        assertEquals(initialFilter, stack[1])
        assertTrue(stack[1] === initialFilter)
    }

    @Test
    fun testReturnQuizToFilterWhenFilterNotInStack() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection, MedQBRoutes.Quiz(sessionId = "session_2"))
        val navigator = AppNavigator(stack)

        navigator.returnQuizToFilter("History")

        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, stack[0])
        assertEquals(MedQBRoutes.Filter(initialPaneName = "History"), stack[1])
    }

    @Test
    fun testRoutesPolymorphicSerialization() {
        val json = Json {
            serializersModule = SerializersModule {
                polymorphic(baseClass = NavKey::class) {
                    subclass(serializer = MedQBRoutes.DatabaseSelection.serializer())
                    subclass(serializer = MedQBRoutes.Filter.serializer())
                    subclass(serializer = MedQBRoutes.Quiz.serializer())
                    subclass(serializer = MedQBRoutes.Settings.serializer())
                    subclass(serializer = MedQBRoutes.MediaViewer.serializer())
                    subclass(serializer = MedQBRoutes.HtmlViewer.serializer())
                }
            }
        }

        val routes: List<NavKey> = listOf(
            MedQBRoutes.DatabaseSelection,
            MedQBRoutes.Filter(initialPaneName = "History"),
            MedQBRoutes.Quiz(sessionId = "123", entryName = "Test Quiz", initialQuestionIndex = 5),
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
