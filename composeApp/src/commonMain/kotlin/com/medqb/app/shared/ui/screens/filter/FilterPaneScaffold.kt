package com.medqb.app.shared.ui.screens.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing

enum class FilterPane(
    val icon: ImageVector,
    val label: String,
) {
    Filters(icon = Icons.Filled.FilterAlt, label = "Filter"),
    History(icon = Icons.Filled.History, label = "History"),
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
                    .padding(Spacing.Large)
                    .navigationBarsPadding()
                    .zIndex(1f),
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
        modifier = modifier.widthIn(max = Layout.MaxContentWidth),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        val panes = FilterPane.entries
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            panes.forEachIndexed { index, pane ->
                FilterPaneItem(
                    pane = pane,
                    selected = selectedPane == pane,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        panes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    onClick = { onPaneSelected(pane) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterPaneItem(
    pane: FilterPane,
    selected: Boolean,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        shapes = shapes,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.semantics { role = Role.Tab },
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                expandHorizontally(motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Start),
            exit = fadeOut(motionScheme.defaultEffectsSpec()) +
                shrinkHorizontally(motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = pane.icon,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
            }
        }
        Text(
            text = pane.label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
