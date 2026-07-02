package com.medqb.app.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(
                checked = selectedPane == FilterPane.Filters,
                onCheckedChange = { if (it) onPaneSelected(FilterPane.Filters) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            ) {
                Icon(Icons.Filled.FilterAlt, contentDescription = null)
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text("Filter")
            }
            ToggleButton(
                checked = selectedPane == FilterPane.History,
                onCheckedChange = { if (it) onPaneSelected(FilterPane.History) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text("History")
            }
        }
    }
}
