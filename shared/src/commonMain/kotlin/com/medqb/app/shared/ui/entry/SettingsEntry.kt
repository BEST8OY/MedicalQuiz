package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.ui.screens.SettingsScreen

@Composable
fun SettingsEntry(
    graph: AppGraph,
    navigator: AppNavigator,
) {
    val settingsVM = viewModel {
        graph.createSettingsViewModel()
    }

    val showMetadata by settingsVM.showMetadata.collectAsStateWithLifecycle()
    val fontScalePreference by settingsVM.fontScalePreference.collectAsStateWithLifecycle()

    SettingsScreen(
        showMetadata = showMetadata,
        fontScalePreference = fontScalePreference,
        onShowMetadataToggle = { settingsVM.setShowMetadata(it) },
        onFontScaleChange = { settingsVM.setFontScalePreference(it) },
        onBack = dropUnlessResumed { navigator.navigateBack() },
    )
}
