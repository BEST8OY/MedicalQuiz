package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
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

/**
 * Connected M3 Expressive study tools section positioned between the Question and Answer options.
 *
 * Consists of:
 * 1. A connected [ButtonGroup] with:
 *    - Key Info (toggleable item)
 *    - Attending Tip (toggleable item)
 *    - Labs (clickable item)
 * 2. A docked shape [Card] directly connected to the bottom of the [ButtonGroup] when
 *    the Attending's Tip is expanded, with non-bouncing monotonic exit transitions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizStudySection(
    hasKeyInfo: Boolean = true,
    isKeyInfoActive: Boolean,
    onToggleKeyInfo: () -> Unit,
    hasAttendingTip: Boolean,
    isAttendingTipActive: Boolean,
    onToggleAttendingTip: () -> Unit,
    onOpenLabs: () -> Unit,
    hasLabs: Boolean = true,
    hintHtml: String? = null,
    linkHandler: (String) -> Unit = {},
    mediaClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!hasKeyInfo && !hasAttendingTip && !hasLabs) return

    val motionScheme = MaterialTheme.motionScheme
    val defaultEffectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = motionScheme.defaultSpatialSpec<IntSize>()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ButtonGroup(
            overflowIndicator = {
                ButtonGroupDefaults.OverflowIndicator(menuState = it)
            },
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            expandedRatio = ButtonGroupDefaults.ExpandedRatio,
        ) {
            if (hasKeyInfo) {
                toggleableItem(
                    checked = isKeyInfoActive,
                    label = "Key info",
                    onCheckedChange = { onToggleKeyInfo() },
                    weight = 1f,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("quiz_action_key_info")
                        )
                    }
                )
            }

            if (hasAttendingTip) {
                toggleableItem(
                    checked = isAttendingTipActive,
                    label = "Attending tip",
                    onCheckedChange = { onToggleAttendingTip() },
                    weight = 1.25f,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("quiz_action_attending_tip")
                        )
                    }
                )
            }

            if (hasLabs) {
                clickableItem(
                    onClick = onOpenLabs,
                    label = "Labs",
                    weight = 1f,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("quiz_action_labs")
                        )
                    }
                )
            }
        }

        // Docked Shape Card for Attending's Tip
        AnimatedVisibility(
            visible = isAttendingTipActive && !hintHtml.isNullOrBlank(),
            enter = fadeIn(animationSpec = defaultEffectsSpec) +
                expandVertically(animationSpec = defaultSpatialSpec),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attending_tip_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Attending's Tip",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        IconButton(
                            onClick = onToggleAttendingTip,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("attending_tip_close")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Attending's Tip",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
                        html = hintHtml.orEmpty(),
                        modifier = Modifier.padding(top = 8.dp),
                        onLinkClick = linkHandler,
                        onMediaClick = mediaClick,
                        showSelectedHighlight = isKeyInfoActive,
                        palette = hintPalette
                    )
                }
            }
        }
    }
}
