package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Quiz study action bar containing AMBOSS-style study tools:
 * - KEY INFO: Toggles highlighting of key informational phrases in question stem.
 * - ATTENDING TIP: Toggles display of the Attending's Tip (hint).
 * - LABS: Opens reference ranges bottom sheet (Serum, Cerebrospinal, Blood, Urine and BMI).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizStudyActionBar(
    hasKeyInfo: Boolean = true,
    isKeyInfoActive: Boolean,
    onToggleKeyInfo: () -> Unit,
    hasAttendingTip: Boolean,
    isAttendingTipActive: Boolean,
    onToggleAttendingTip: () -> Unit,
    onOpenLabs: () -> Unit,
    hasLabs: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!hasKeyInfo && !hasAttendingTip && !hasLabs) return

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // KEY INFO button - only displayed if key info is present
        if (hasKeyInfo) {
            StudyToolButton(
                label = "Key info",
                icon = Icons.Rounded.VpnKey,
                isActive = isKeyInfoActive,
                onClick = onToggleKeyInfo,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "quiz_action_key_info"
            )
        }

        // ATTENDING TIP button - only displayed if a hint exists
        if (hasAttendingTip) {
            StudyToolButton(
                label = "Attending tip",
                icon = Icons.Rounded.Lightbulb,
                isActive = isAttendingTipActive,
                onClick = onToggleAttendingTip,
                activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                testTag = "quiz_action_attending_tip"
            )
        }

        // LABS button - launches modal reference values
        if (hasLabs) {
            AssistChip(
                onClick = onOpenLabs,
                shape = MaterialTheme.shapes.small,
                label = {
                    Text(
                        text = "Labs",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Science,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true
                ),
                modifier = Modifier.testTag("quiz_action_labs")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StudyToolButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    testTag: String = ""
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        label = {
            Text(
                text = label,
                style = if (isActive) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = activeContainerColor,
            selectedLabelColor = activeContentColor,
            selectedLeadingIconColor = activeContentColor,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = isActive
        ),
        modifier = modifier.testTag(testTag)
    )
}
