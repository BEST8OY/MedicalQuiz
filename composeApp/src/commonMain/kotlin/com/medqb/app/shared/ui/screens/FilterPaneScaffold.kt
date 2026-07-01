package com.medqb.app.shared.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.components.PaneToggleButton

enum class FilterPane {
    Filters,
    History,
}

@Composable
internal fun FilterPaneScaffold(
    selectedPane: FilterPane,
    onPaneSelected: (FilterPane) -> Unit,
    showPaneToolbar: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            content()
        }

        if (showPaneToolbar) {
            FilterPaneFloatingToolbar(
                selectedPane = selectedPane,
                onPaneSelected = onPaneSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterPaneFloatingToolbar(
    selectedPane: FilterPane,
    onPaneSelected: (FilterPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        PaneToggleButton(
            checked = selectedPane == FilterPane.Filters,
            label = "Filter",
            icon = Icons.Filled.FilterAlt,
            onCheckedChange = { if (it) onPaneSelected(FilterPane.Filters) },
        )

        PaneToggleButton(
            checked = selectedPane == FilterPane.History,
            label = "History",
            icon = Icons.Filled.History,
            onCheckedChange = { if (it) onPaneSelected(FilterPane.History) },
        )
    }
}
