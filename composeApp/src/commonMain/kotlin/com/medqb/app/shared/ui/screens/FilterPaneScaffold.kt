package com.medqb.app.shared.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.theme.AppShapes
import com.medqb.app.shared.ui.theme.Inset
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
                    .padding(Spacing.Lg)
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
        modifier = modifier.widthIn(max = Layout.MaxContentWidth),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterPane.entries.forEach { pane ->
                FilterPaneItem(
                    pane = pane,
                    selected = selectedPane == pane,
                    onClick = { onPaneSelected(pane) },
                )
            }
        }
    }
}

@Composable
private fun FilterPaneItem(
    pane: FilterPane,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = AppShapes.ToolbarPillShape
    val motionScheme = MaterialTheme.motionScheme
    val transition = updateTransition(targetState = selected, label = "filterItem_${pane.name}")

    val containerColor by transition.animateColor(
        transitionSpec = { motionScheme.defaultEffectsSpec() },
        label = "containerColor",
    ) { isSelected ->
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    val contentColor by transition.animateColor(
        transitionSpec = { motionScheme.defaultEffectsSpec() },
        label = "contentColor",
    ) { isSelected ->
        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val iconScale by transition.animateFloat(
        transitionSpec = { motionScheme.defaultSpatialSpec() },
        label = "iconScale",
    ) { isSelected -> if (isSelected) 1.2f else 1.0f }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "pressScale",
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "animatedContainer",
    )

    Row(
        modifier = Modifier
            .scale(pressScale)
            .clip(shape)
            .background(animatedContainerColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .widthIn(min = Layout.MinTouchTarget)
            .padding(horizontal = Inset.Sm, vertical = Inset.Sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = pane.icon,
            contentDescription = pane.label,
            tint = contentColor,
            modifier = Modifier.scale(iconScale),
        )
        Spacer(modifier = Modifier.size(Spacing.Sm))
        Text(
            text = pane.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
