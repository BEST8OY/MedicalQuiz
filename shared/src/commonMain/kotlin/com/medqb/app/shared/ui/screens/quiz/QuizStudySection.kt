package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.generated.resources.Res
import com.medqb.app.shared.generated.resources.ic_avatar_01
import com.medqb.app.shared.generated.resources.ic_avatar_02
import com.medqb.app.shared.generated.resources.ic_avatar_03
import com.medqb.app.shared.generated.resources.ic_avatar_04
import com.medqb.app.shared.generated.resources.ic_avatar_05
import com.medqb.app.shared.generated.resources.ic_avatar_06
import com.medqb.app.shared.generated.resources.ic_avatar_07
import com.medqb.app.shared.generated.resources.ic_avatar_08
import com.medqb.app.shared.generated.resources.ic_avatar_09
import com.medqb.app.shared.generated.resources.ic_avatar_10
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextPalette
import org.jetbrains.compose.resources.painterResource

private val DoctorAvatars = listOf(
    Res.drawable.ic_avatar_01,
    Res.drawable.ic_avatar_02,
    Res.drawable.ic_avatar_03,
    Res.drawable.ic_avatar_04,
    Res.drawable.ic_avatar_05,
    Res.drawable.ic_avatar_06,
    Res.drawable.ic_avatar_07,
    Res.drawable.ic_avatar_08,
    Res.drawable.ic_avatar_09,
    Res.drawable.ic_avatar_10,
)

/**
 * Connected M3 Expressive study tools section positioned between the Question and Answer options.
 *
 * Consists of:
 * 1. A connected [ButtonGroup] with balanced peer styling:
 *    - Key Info (toggleable item)
 *    - Attending Tip (toggleable item)
 *    - Labs (toggleable item)
 * 2. An Attending's Tip [Card] showing a randomly assigned doctor avatar on the upper left
 *    and the formatted rich-text clinical pearl next to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizStudySection(
    questionId: Long = 0L,
    hasKeyInfo: Boolean = true,
    isKeyInfoActive: Boolean,
    onToggleKeyInfo: () -> Unit,
    hasAttendingTip: Boolean,
    isAttendingTipActive: Boolean,
    onToggleAttendingTip: () -> Unit,
    hasLabs: Boolean = true,
    isLabsActive: Boolean = false,
    onOpenLabs: () -> Unit,
    hintHtml: String? = null,
    linkHandler: (String) -> Unit = {},
    mediaClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!hasKeyInfo && !hasAttendingTip && !hasLabs) return

    val motionScheme = MaterialTheme.motionScheme
    val defaultEffectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = motionScheme.defaultSpatialSpec<IntSize>()

    val avatar = remember(questionId) {
        if (questionId != 0L) {
            val index = ((questionId and 0x7FFFFFFF) % DoctorAvatars.size).toInt()
            DoctorAvatars[index]
        } else {
            DoctorAvatars.first()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                toggleableItem(
                    checked = isLabsActive,
                    label = "Labs",
                    onCheckedChange = { onOpenLabs() },
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

        // Attending's Tip Card
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
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attending_tip_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(avatar),
                        contentDescription = "Attending doctor avatar",
                        modifier = Modifier
                            .size(width = 48.dp, height = 53.dp)
                            .testTag("attending_doctor_avatar"),
                        contentScale = ContentScale.Fit
                    )

                    val colors = MaterialTheme.colorScheme
                    val hintPalette = remember(colors) {
                        RichTextPalette(
                            importantBackground = colors.tertiaryContainer,
                            importantText = colors.onTertiaryContainer,
                            selectedBackground = colors.primaryContainer,
                            selectedText = colors.onPrimaryContainer,
                            linkText = colors.primary,
                            dictionaryText = colors.secondary,
                            abstractText = colors.onSurfaceVariant
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        RichText(
                            html = hintHtml.orEmpty(),
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
}
