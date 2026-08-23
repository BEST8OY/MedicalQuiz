package com.medqb.app.shared.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @Test
    fun reflectsDisabledShowMetadataImmediatelyWithoutFlash() {
        val fakeRepo = FakeSettingsRepository(showMetadata = false)
        val viewModel = SettingsViewModel(fakeRepo)

        // Must be false immediately on creation — no flash of default true
        assertFalse(viewModel.showMetadata.value)
    }

    @Test
    fun togglingShowMetadataUpdatesRepositoryAndState() {
        val fakeRepo = FakeSettingsRepository(showMetadata = true)
        val viewModel = SettingsViewModel(fakeRepo)

        assertTrue(viewModel.showMetadata.value)

        viewModel.setShowMetadata(false)
        assertFalse(viewModel.showMetadata.value)
        assertFalse(fakeRepo.showMetadata.value)

        viewModel.setShowMetadata(true)
        assertTrue(viewModel.showMetadata.value)
        assertTrue(fakeRepo.showMetadata.value)
    }

    @Test
    fun fontScalePreferenceReflectsStoredValueAndUpdates() {
        val fakeRepo = FakeSettingsRepository(fontScalePreference = 1.25f)
        val viewModel = SettingsViewModel(fakeRepo)

        assertEquals(1.25f, viewModel.fontScalePreference.value)

        viewModel.setFontScalePreference(1.5f)
        assertEquals(1.5f, viewModel.fontScalePreference.value)
        assertEquals(1.5f, fakeRepo.fontScalePreference.value)

        viewModel.setFontScalePreference(null)
        assertNull(viewModel.fontScalePreference.value)
        assertNull(fakeRepo.fontScalePreference.value)
    }
}
