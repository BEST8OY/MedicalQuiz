package com.medqb.app.shared.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.FontScalePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {

    @Test
    fun displaysAllSettingsSectionsByDefault() = runComposeUiTest {
        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = null,
                onShowMetadataToggle = {},
                onFontScaleChange = {},
                onBack = {},
            )
        }

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithContentDescription("Back").assertIsDisplayed()

        // Section 1
        onNodeWithText("Quiz Experience").assertIsDisplayed()
        onNodeWithText("Show metadata").assertIsDisplayed()
        onNodeWithText("Display subjects and systems after answering").assertIsDisplayed()

        // Section 2
        onNodeWithText("Appearance & Accessibility").assertIsDisplayed()
        onNodeWithText("Use system font size").assertIsDisplayed()
        onNodeWithText("Match the font size to your device system settings").assertIsDisplayed()

        // When system size is enabled, custom slider and preview should not exist in semantics tree
        onNodeWithText("Custom Reading Text Size").assertDoesNotExist()
        onNodeWithText("LIVE PREVIEW").assertDoesNotExist()

        // Scope notice
        onNodeWithText("Custom typography sizes apply exclusively to medical questions, answers, and media rich text descriptions.").assertIsDisplayed()
    }

    @Test
    fun togglesShowMetadata() = runComposeUiTest {
        var toggledValue: Boolean? = null

        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = null,
                onShowMetadataToggle = { toggledValue = it },
                onFontScaleChange = {},
                onBack = {},
            )
        }

        onNodeWithText("Show metadata").performClick()
        assertEquals(false, toggledValue)
    }

    @Test
    fun customFontSizeRevealsSliderAndLivePreview() = runComposeUiTest {
        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = FontScalePresets.DEFAULT,
                onShowMetadataToggle = {},
                onFontScaleChange = {},
                onBack = {},
            )
        }

        onNodeWithText("Custom Reading Text Size").assertIsDisplayed()
        onNodeWithText("Normal Default Size (1.0×)").assertIsDisplayed()
        onNodeWithText("LIVE PREVIEW").assertIsDisplayed()
        onNodeWithText("A 62-year-old female presents with progressive shortness of breath, bilateral ankle swelling, and orthopnea.").assertIsDisplayed()
        onNodeWithText("Congestive Heart Failure").assertIsDisplayed()
        onNodeWithText("Acute Pulmonary Embolism").assertIsDisplayed()
    }

    @Test
    fun togglingUseSystemFontSizeSwitchesToCustomScale() = runComposeUiTest {
        var newScale: Float? = Float.MIN_VALUE

        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = null, // currently system font size (checked = true)
                onShowMetadataToggle = {},
                onFontScaleChange = { newScale = it },
                onBack = {},
            )
        }

        // Tapping the row unchecks system font size, requesting DEFAULT custom scale
        onNodeWithText("Use system font size").performClick()
        assertEquals(FontScalePresets.DEFAULT, newScale)
    }

    @Test
    fun togglingUseSystemFontSizeWhenDisabledRestoresNull() = runComposeUiTest {
        var restoredScale: Float? = Float.MIN_VALUE

        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = FontScalePresets.LARGE, // custom size is active (checked = false)
                onShowMetadataToggle = {},
                onFontScaleChange = { restoredScale = it },
                onBack = {},
            )
        }

        // Tapping the row checks system font size, requesting null
        onNodeWithText("Use system font size").performClick()
        assertNull(restoredScale)
    }

    @Test
    fun backButtonTriggersOnBack() = runComposeUiTest {
        var backTriggered = false

        setContent {
            SettingsScreen(
                showMetadata = true,
                fontScalePreference = null,
                onShowMetadataToggle = {},
                onFontScaleChange = {},
                onBack = { backTriggered = true },
            )
        }

        onNodeWithContentDescription("Back").performClick()
        assertTrue(backTriggered)
    }
}
