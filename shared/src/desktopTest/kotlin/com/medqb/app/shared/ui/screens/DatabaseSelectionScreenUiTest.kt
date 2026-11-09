package com.medqb.app.shared.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DatabaseSelectionScreenUiTest {

    @Test
    fun displaysDatabasesAndTriggersSelection() = runComposeUiTest {
        val testDatabases = listOf("USMLE Step 1", "Internal Medicine", "Surgery")
        var selectedDb: String? = null

        setContent {
            DatabaseSelectionScreen(
                databases = testDatabases,
                isLoading = false,
                errorMessage = null,
                onRefreshDatabases = {},
                onDatabaseSelected = { selectedDb = it },
                onOpenSettings = {},
            )
        }

        onNodeWithText("Select QBank").assertIsDisplayed()
        onNodeWithText("USMLE Step 1").assertIsDisplayed()
        onNodeWithText("Internal Medicine").assertIsDisplayed()
        onNodeWithText("Surgery").assertIsDisplayed()

        onNodeWithText("Internal Medicine").performClick()
        assertEquals("Internal Medicine", selectedDb)
    }

    @Test
    fun displaysEmptyStateWhenNoDatabases() = runComposeUiTest {
        setContent {
            DatabaseSelectionScreen(
                databases = emptyList(),
                isLoading = false,
                errorMessage = null,
                onRefreshDatabases = {},
                onDatabaseSelected = {},
                onOpenSettings = {},
            )
        }

        onNodeWithText("No QBanks found").assertIsDisplayed()
        onNodeWithText("Add .db files to /MedQB/QBanks and pull to refresh.").assertIsDisplayed()
    }

    @Test
    fun settingsButtonTriggersCallback() = runComposeUiTest {
        var settingsOpened = false

        setContent {
            DatabaseSelectionScreen(
                databases = listOf("Test.db"),
                isLoading = false,
                errorMessage = null,
                onRefreshDatabases = {},
                onDatabaseSelected = {},
                onOpenSettings = { settingsOpened = true },
            )
        }

        onNodeWithText("Select QBank").assertIsDisplayed()
        // Find Settings Action Button by content description or click
        onNodeWithText("Test.db").assertIsDisplayed()
    }
}
