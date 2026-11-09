package com.medqb.app.shared.ui.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StatusFilterBottomSheetUiTest {

    @Test
    fun displaysAllStatusesAndScopesAndHandlesMultiSelection() = runComposeUiTest {
        var appliedFilter: StatusFilter? = null
        var dismissed = false

        setContent {
            StatusFilterBottomSheet(
                current = StatusFilter.DEFAULT,
                onSelect = { appliedFilter = it },
                onDismiss = { dismissed = true }
            )
        }

        // Verify title & section headers
        onNodeWithText("Question Status").assertIsDisplayed()
        onNodeWithText("INCLUDE QUESTIONS WITH FOLLOWING STATUS:").assertIsDisplayed()
        onNodeWithText("HOW SHOULD STATUS FILTERS BE APPLIED?").assertIsDisplayed()

        // Verify all 3 status checkbox options exist and default to OFF (no option selected)
        onNodeWithTag("status_checkbox_not_yet_answered").assertIsDisplayed().assertIsOff()
        onNodeWithTag("status_checkbox_answered_incorrectly").assertIsDisplayed().assertIsOff()
        onNodeWithTag("status_checkbox_answered_correctly").assertIsDisplayed().assertIsOff()

        // Verify both scope radio options exist and default to ALL_ATTEMPTS
        onNodeWithTag("status_scope_all_attempts").assertIsDisplayed().assertIsSelected()
        onNodeWithTag("status_scope_latest_attempt").assertIsDisplayed().assertIsNotSelected()

        // Multi-select: Check "Answered incorrectly"
        onNodeWithTag("status_checkbox_answered_incorrectly").performClick().assertIsOn()

        // Switch scope: Select "Based on most recent question attempt"
        onNodeWithTag("status_scope_latest_attempt").performClick().assertIsSelected()
        onNodeWithTag("status_scope_all_attempts").assertIsNotSelected()

        // Click Apply
        onNodeWithTag("status_apply_button").performClick()

        val filter = assertNotNull(appliedFilter)
        assertEquals(
            setOf(QuestionStatus.ANSWERED_INCORRECTLY),
            filter.statuses
        )
        assertEquals(StatusScope.LATEST_ATTEMPT, filter.scope)
    }

    @Test
    fun uncheckingStatusRemovesFromSelection() = runComposeUiTest {
        var appliedFilter: StatusFilter? = null

        setContent {
            StatusFilterBottomSheet(
                current = StatusFilter(
                    statuses = setOf(QuestionStatus.NOT_YET_ANSWERED, QuestionStatus.ANSWERED_CORRECTLY),
                    scope = StatusScope.LATEST_ATTEMPT
                ),
                onSelect = { appliedFilter = it },
                onDismiss = {}
            )
        }

        // Verify both checkboxes are checked
        onNodeWithTag("status_checkbox_not_yet_answered").assertIsOn()
        onNodeWithTag("status_checkbox_answered_correctly").assertIsOn()

        // Uncheck "Answered correctly"
        onNodeWithTag("status_checkbox_answered_correctly").performClick().assertIsOff()

        // Click Apply
        onNodeWithTag("status_apply_button").performClick()

        val filter = assertNotNull(appliedFilter)
        assertEquals(
            setOf(QuestionStatus.NOT_YET_ANSWERED),
            filter.statuses
        )
    }

    @Test
    fun resetRestoresDefaultState() = runComposeUiTest {
        var appliedFilter: StatusFilter? = null

        setContent {
            StatusFilterBottomSheet(
                current = StatusFilter(
                    statuses = setOf(QuestionStatus.ANSWERED_CORRECTLY),
                    scope = StatusScope.ALL_ATTEMPTS
                ),
                onSelect = { appliedFilter = it },
                onDismiss = {}
            )
        }

        // Reset button should be enabled since current != DEFAULT
        onNodeWithTag("status_reset_button").assertIsEnabled().performClick()

        // Should restore DEFAULT (no option selected + All attempts)
        onNodeWithTag("status_checkbox_not_yet_answered").assertIsOff()
        onNodeWithTag("status_checkbox_answered_correctly").assertIsOff()
        onNodeWithTag("status_scope_all_attempts").assertIsSelected()

        onNodeWithTag("status_apply_button").performClick()
        assertEquals(StatusFilter.DEFAULT, appliedFilter)
    }

    @Test
    fun cancelInvokesDismiss() = runComposeUiTest {
        var dismissed = false

        setContent {
            StatusFilterBottomSheet(
                current = StatusFilter.DEFAULT,
                onSelect = {},
                onDismiss = { dismissed = true }
            )
        }

        onNodeWithTag("status_cancel_button").performClick()
        assertTrue(dismissed)
    }
}
