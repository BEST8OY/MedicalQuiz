package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextPalette
import com.medqb.app.shared.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HintSection(
    isVisible: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
    hintHtml: String,
    linkHandler: (String) -> Unit,
    mediaClick: (String) -> Unit,
    showSelectedHighlight: Boolean
) {
    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isVisible) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
        onClick = if (canToggle) onToggle else ({})
    ) {
        Column(modifier = Modifier.padding(Spacing.MediumSmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hintContentColor = if (isVisible) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = hintContentColor
                )
                Text(
                    text = "Hint",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = hintContentColor
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canToggle) {
                    Icon(
                        imageVector = if (isVisible) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = hintContentColor
                    )
                }
            }
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
                    animationSpec = defaultSpatialSpec,
                )
            ) {
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
