package com.medqb.app.shared.ui.screens.filter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FilterScreenUiTest {

    @Test
    fun displaysFilterCategoriesAndTriggersCallbacks() = runComposeUiTest {
        var subjectsClicked = false
        var systemsClicked = false
        var statusClicked = false
        var difficultyClicked = false

        setContent {
            FilterScreen(
                databaseName = "USMLE Step 1",
                subjectCount = 0,
                excludedSubjectCount = 0,
                systemCount = 0,
                excludedSystemCount = 0,
                statusFilter = StatusFilter.DEFAULT,
                statusLabel = "All Questions",
                selectedDifficultyTiers = emptySet(),
                difficultyLabel = "All Difficulties",
                isDifficultyAvailable = true,
                previewCount = 150,
                isLoggingEnabled = true,
                onLoggingToggle = {},
                submissionMode = SubmissionMode.INSTANT,
                onSubmissionModeToggle = {},
                onSelectSubjects = { subjectsClicked = true },
                onSelectSystems = { systemsClicked = true },
                onSelectStatus = { statusClicked = true },
                onSelectDifficulty = { difficultyClicked = true },
                onStart = {},
                onClearFilters = {},
            )
        }

        onNodeWithText("USMLE Step 1").assertIsDisplayed()
        onNodeWithText("150 questions available").assertIsDisplayed()

        onNodeWithText("Subjects").assertIsDisplayed()
        onNodeWithText("All subjects").assertIsDisplayed()
        onNodeWithText("Systems").assertIsDisplayed()
        onNodeWithText("All systems").assertIsDisplayed()
        onNodeWithText("Status").assertIsDisplayed()
        onNodeWithText("All Questions").assertIsDisplayed()
        onNodeWithText("Difficulty").assertIsDisplayed()
        onNodeWithText("All Difficulties").assertIsDisplayed()

        onNodeWithText("Subjects").performClick()
        assertTrue(subjectsClicked, "Subjects should trigger callback on click")

        onNodeWithText("Systems").performClick()
        assertTrue(systemsClicked, "Systems should trigger callback on click")

        onNodeWithText("Status").performClick()
        assertTrue(statusClicked, "Status should trigger callback on click")

        onNodeWithText("Difficulty").performClick()
        assertTrue(difficultyClicked, "Difficulty should trigger callback on click")
    }

    @Test
    fun togglesSessionProgressAndSubmissionMode() = runComposeUiTest {
        var toggledLogging: Boolean? = null
        var toggledMode: SubmissionMode? = null

        setContent {
            FilterScreen(
                databaseName = "Internal Medicine",
                subjectCount = 0,
                systemCount = 0,
                statusFilter = StatusFilter.DEFAULT,
                statusLabel = "All Questions",
                previewCount = 50,
                isLoggingEnabled = true,
                onLoggingToggle = { toggledLogging = it },
                submissionMode = SubmissionMode.INSTANT,
                onSubmissionModeToggle = { toggledMode = it },
                onSelectSubjects = {},
                onSelectSystems = {},
                onSelectStatus = {},
                onStart = {},
                onClearFilters = {},
            )
        }

        onNodeWithText("Track Session Progress").assertIsDisplayed()
        onNodeWithText("Record answer logs for historical tracking").assertIsDisplayed()
        onNodeWithText("Manual Submission").assertIsDisplayed()
        onNodeWithText("Review your answer before submitting").assertIsDisplayed()

        onNodeWithText("Track Session Progress").performClick()
        assertEquals(false, toggledLogging, "Clicking logging toggle should pass negated boolean")

        onNodeWithText("Manual Submission").performClick()
        assertEquals(SubmissionMode.MANUAL, toggledMode, "Clicking manual submission should toggle to MANUAL")
    }

    @Test
    fun displaysDisabledDifficultyWhenNotAvailable() = runComposeUiTest {
        var difficultyClicked = false

        setContent {
            FilterScreen(
                databaseName = "Basic Bank",
                subjectCount = 0,
                systemCount = 0,
                statusFilter = StatusFilter.DEFAULT,
                statusLabel = "All Questions",
                isDifficultyAvailable = false,
                previewCount = 10,
                isLoggingEnabled = true,
                onLoggingToggle = {},
                submissionMode = SubmissionMode.INSTANT,
                onSubmissionModeToggle = {},
                onSelectSubjects = {},
                onSelectSystems = {},
                onSelectStatus = {},
                onSelectDifficulty = { difficultyClicked = true },
                onStart = {},
                onClearFilters = {},
            )
        }

        onNodeWithText("Difficulty").assertIsDisplayed()
        onNodeWithText("Not available for this bank").assertIsDisplayed()

        onNodeWithText("Difficulty").performClick()
        assertEquals(false, difficultyClicked, "Disabled difficulty row should not trigger click callback")
    }

    @Test
    fun displaysActiveFilterStateAndSubtitles() = runComposeUiTest {
        setContent {
            FilterScreen(
                databaseName = "Surgery Bank",
                subjectCount = 3,
                excludedSubjectCount = 1,
                systemCount = 2,
                excludedSystemCount = 0,
                statusFilter = StatusFilter(
                    statuses = setOf(QuestionStatus.ANSWERED_INCORRECTLY),
                    scope = StatusScope.LATEST_ATTEMPT
                ),
                statusLabel = "Incorrect",
                selectedDifficultyTiers = setOf(DifficultyTier.EASY, DifficultyTier.INTERMEDIATE),
                difficultyLabel = "Easy, Intermediate",
                isDifficultyAvailable = true,
                previewCount = 25,
                isLoggingEnabled = false,
                onLoggingToggle = {},
                submissionMode = SubmissionMode.MANUAL,
                onSubmissionModeToggle = {},
                onSelectSubjects = {},
                onSelectSystems = {},
                onSelectStatus = {},
                onStart = {},
                onClearFilters = {},
            )
        }

        onNodeWithText("3 selected • 1 excluded").assertIsDisplayed()
        onNodeWithText("2 selected").assertIsDisplayed()
        onNodeWithText("Incorrect").assertIsDisplayed()
        onNodeWithText("Easy, Intermediate").assertIsDisplayed()
        onNodeWithText("25 questions available").assertIsDisplayed()
    }

    @Test
    fun displaysResetButtonWhenOnlyExclusionsAreActive() = runComposeUiTest {
        var resetClicked = false

        setContent {
            FilterScreen(
                databaseName = "Surgery Bank",
                subjectCount = 0,
                excludedSubjectCount = 2,
                systemCount = 0,
                excludedSystemCount = 0,
                statusFilter = StatusFilter.DEFAULT,
                statusLabel = "All Questions",
                previewCount = 25,
                isLoggingEnabled = true,
                onLoggingToggle = {},
                submissionMode = SubmissionMode.INSTANT,
                onSubmissionModeToggle = {},
                onSelectSubjects = {},
                onSelectSystems = {},
                onSelectStatus = {},
                onStart = {},
                onClearFilters = { resetClicked = true },
            )
        }

        onNodeWithText("Reset")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(resetClicked, "Reset button should be clickable and trigger callback when only exclusions are active")
    }

    @Test
    fun disablesStartQuizWhenPreviewCountIsZero() = runComposeUiTest {
        var startClicked = false

        setContent {
            FilterScreen(
                databaseName = "Empty Match Bank",
                subjectCount = 1,
                systemCount = 1,
                statusFilter = StatusFilter.DEFAULT,
                statusLabel = "All Questions",
                previewCount = 0,
                isLoggingEnabled = true,
                onLoggingToggle = {},
                submissionMode = SubmissionMode.INSTANT,
                onSubmissionModeToggle = {},
                onSelectSubjects = {},
                onSelectSystems = {},
                onSelectStatus = {},
                onStart = { startClicked = true },
                onClearFilters = {},
            )
        }

        onNodeWithText("No matches")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
        onNodeWithText("No matches").performClick()
        assertFalse(startClicked, "onStart should not be invoked when previewCount is zero")
    }

    @Test
    fun rendersWideScreenLayoutProperly() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                FilterScreen(
                    databaseName = "USMLE Step 1",
                    subjectCount = 2,
                    systemCount = 3,
                    statusFilter = StatusFilter.DEFAULT,
                    statusLabel = "All Questions",
                    previewCount = 120,
                    isLoggingEnabled = true,
                    onLoggingToggle = {},
                    submissionMode = SubmissionMode.INSTANT,
                    onSubmissionModeToggle = {},
                    onSelectSubjects = {},
                    onSelectSystems = {},
                    onSelectStatus = {},
                    onStart = {},
                    onClearFilters = {},
                )
            }
        }

        onNodeWithText("USMLE Step 1").assertIsDisplayed()
        onNodeWithText("120 questions available").assertIsDisplayed()
        onNodeWithText("Subjects").assertIsDisplayed()
        onNodeWithText("Systems").assertIsDisplayed()
        onNodeWithText("Status").assertIsDisplayed()
    }
}
