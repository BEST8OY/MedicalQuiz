package com.medqb.app.shared.navigation

import androidx.navigation3.runtime.NavKey
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

        navigator.navigateTo(MedQBRoutes.Filter())
        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.Filter(), navigator.currentRoute)

        val popped = navigator.navigateBack()
        assertTrue(popped)
        assertEquals(1, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, navigator.currentRoute)

        val poppedRoot = navigator.navigateBack()
        assertFalse(poppedRoot)
        assertEquals(1, stack.size)
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
    fun testReturnQuizToFilterWhenFilterNotInStack() {
        val stack = mutableListOf<NavKey>(MedQBRoutes.DatabaseSelection, MedQBRoutes.Quiz(sessionId = "session_2"))
        val navigator = AppNavigator(stack)

        navigator.returnQuizToFilter("History")

        assertEquals(2, stack.size)
        assertEquals(MedQBRoutes.DatabaseSelection, stack[0])
        assertEquals(MedQBRoutes.Filter(initialPaneName = "History"), stack[1])
    }
}
