package com.medqb.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medqb.app.shared.data.FontScalePresets
import com.medqb.app.shared.ui.richtext.scaledBy
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    showMetadata: Boolean,
    fontScalePreference: Float?,
    onShowMetadataToggle: (Boolean) -> Unit,
    onFontScaleChange: (Float?) -> Unit,
    onBack: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    val useSystemSize = fontScalePreference == null
    val currentScale = fontScalePreference ?: FontScalePresets.DEFAULT

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = ScreenLayout.WideWidthBreakpoint)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.LgSm, vertical = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.LgSm)
            ) {
            // Section 1: Quiz Experience
            Text(
                text = "Quiz Experience",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.Xxs)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Show metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Display subjects and systems after answering",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = showMetadata,
                            onCheckedChange = onShowMetadataToggle
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onSurface,
                        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = Spacing.Xxs)
                )
            }

            // Section 2: Text & Accessibility
            Text(
                text = "Appearance & Accessibility",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.Xxs)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = Spacing.Md)
                        .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec())
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Use system font size",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "Match the font size to your device system settings",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                        Switch(
                            checked = useSystemSize,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onFontScaleChange(null)
                                } else {
                                    onFontScaleChange(FontScalePresets.DEFAULT)
                                }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.onSurface,
                            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(vertical = Spacing.Xxs)
                    )

                    AnimatedVisibility(
                        visible = !useSystemSize,
                        enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.Md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                        ) {
                            Text(
                                text = "Custom Reading Text Size",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Spacing.Md)
                                )

                                val sliderIndex = scaleToIndex(currentScale)
                                Slider(
                                    value = sliderIndex,
                                    onValueChange = { indexFloat ->
                                        val newScale = indexToScale(indexFloat.toInt())
                                        onFontScaleChange(newScale)
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                    modifier = Modifier.weight(1f)
                                )

                                Icon(
                                    imageVector = Icons.Outlined.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Spacing.LgSm)
                                )
                            }

                            Text(
                                text = scaleToLabel(currentScale),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = Modifier.height(Spacing.Xxs))

                            LivePreviewCard(currentScale = currentScale)
                        }
                    }
                }
            }

            // Info Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ElementSize.IconMd)
                    )
                    Text(
                        text = "Custom typography sizes apply exclusively to medical questions, answers, and media rich text descriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
}

@Composable
private fun LivePreviewCard(currentScale: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        border = BorderStroke(
            width = Stroke.Thin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Text(
                text = "LIVE PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.sp
            )

            Text(
                text = "A 62-year-old female presents with progressive shortness of breath, bilateral ankle swelling, and orthopnea.",
                style = MaterialTheme.typography.bodyMedium.scaledBy(currentScale),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XxsPlus)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(ElementSize.IconMd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = "Congestive Heart Failure",
                        style = MaterialTheme.typography.bodySmall.scaledBy(currentScale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(ElementSize.IconMd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "B",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = "Acute Pulmonary Embolism",
                        style = MaterialTheme.typography.bodySmall.scaledBy(currentScale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun indexToScale(index: Int): Float = when (index) {
    0 -> FontScalePresets.COMPACT
    1 -> FontScalePresets.DEFAULT
    2 -> FontScalePresets.LARGE
    3 -> FontScalePresets.EXTRA_LARGE
    else -> FontScalePresets.DEFAULT
}

private fun scaleToIndex(scale: Float?): Float = when (scale) {
    FontScalePresets.COMPACT -> 0f
    FontScalePresets.DEFAULT -> 1f
    FontScalePresets.LARGE -> 2f
    FontScalePresets.EXTRA_LARGE -> 3f
    else -> 1f
}

private fun scaleToLabel(scale: Float): String = when (scale) {
    FontScalePresets.COMPACT -> "Compact Size (0.9×)"
    FontScalePresets.DEFAULT -> "Normal Default Size (1.0×)"
    FontScalePresets.LARGE -> "Large Size (1.15×)"
    FontScalePresets.EXTRA_LARGE -> "Extra Large Size (1.3×)"
    else -> "Normal Default Size (1.0×)"
}
