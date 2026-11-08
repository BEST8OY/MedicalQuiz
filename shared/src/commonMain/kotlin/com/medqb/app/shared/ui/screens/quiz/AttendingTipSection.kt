package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextPalette
import com.medqb.app.shared.ui.theme.Spacing

/**
 * Refactored Attending's Tip (Hint) card with animated disclosure and dismiss affordance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttendingTipSection(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    hintHtml: String,
    linkHandler: (String) -> Unit,
    mediaClick: (String) -> Unit,
    showSelectedHighlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = defaultEffectsSpec,
        ) + expandVertically(
            animationSpec = defaultSpatialSpec,
        ),
        exit = fadeOut(
            animationSpec = defaultEffectsSpec,
        ) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("attending_tip_card")
        ) {
            Column(modifier = Modifier.padding(Spacing.Medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hintContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = hintContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Attending's Tip",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = hintContentColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("attending_tip_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Attending's Tip",
                            tint = hintContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val colors = MaterialTheme.colorScheme
                val hintPalette = remember(colors) {
                    RichTextPalette(
                        importantBackground = colors.tertiaryContainer,
                        importantText = colors.onTertiaryContainer,
                        selectedBackground = colors.primaryContainer,
                        selectedText = colors.onPrimaryContainer,
                        linkText = colors.onTertiaryContainer,
                        dictionaryText = colors.onTertiaryContainer,
                        abstractText = colors.onSurfaceVariant
                    )
                }
                RichText(
                    html = hintHtml,
                    modifier = Modifier.padding(top = Spacing.Small),
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick,
                    showSelectedHighlight = showSelectedHighlight,
                    palette = hintPalette
                )
            }
        }
    }
}
