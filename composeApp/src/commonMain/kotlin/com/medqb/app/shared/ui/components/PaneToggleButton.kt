package com.medqb.app.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaneToggleButton(
    checked: Boolean,
    label: String,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.padding(horizontal = 4.dp),
        shapes = ToggleButtonDefaults.shapes(
            shape = ToggleButtonDefaults.squareShape,
            pressedShape = ToggleButtonDefaults.roundShape,
            checkedShape = ToggleButtonDefaults.roundShape,
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = expandHorizontally(motionScheme.defaultSpatialSpec(), Alignment.Start) +
                    fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkHorizontally(motionScheme.defaultSpatialSpec(), Alignment.Start) +
                   fadeOut(motionScheme.defaultEffectsSpec()),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }
}
