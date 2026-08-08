package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing

internal enum class MediaControlsLayout {
    None,
    OverlayOnly,
    InfoOnly,
    OverlayAndInfo,
}

internal fun resolveControlsLayout(hasOverlay: Boolean, hasDescription: Boolean): MediaControlsLayout = when {
    hasOverlay && hasDescription -> MediaControlsLayout.OverlayAndInfo
    hasOverlay -> MediaControlsLayout.OverlayOnly
    hasDescription -> MediaControlsLayout.InfoOnly
    else -> MediaControlsLayout.None
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaViewerTopBar(
    showUI: Boolean,
    mediaFilesCount: Int,
    pagerState: PagerState,
    currentFileName: String,
    onBack: () -> Unit,
    onSaveMedia: ((String) -> Unit)?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val transitionAlpha = animatedVisibilityScope?.transition?.animateFloat(
        transitionSpec = { MaterialTheme.motionScheme.defaultEffectsSpec() },
        label = "chromeAlpha"
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.PreEnter -> 0f
            androidx.compose.animation.EnterExitState.Visible -> 1f
            androidx.compose.animation.EnterExitState.PostExit -> 0f
        }
    }?.value ?: 1f

    AnimatedVisibility(
        visible = showUI,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
        modifier = modifier.graphicsLayer { alpha = transitionAlpha },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }

            if (mediaFilesCount > 1) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / $mediaFilesCount",
                        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onSaveMedia != null) {
                FilledIconButton(
                    onClick = { onSaveMedia(currentFileName) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = "Save media",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaViewerBottomBar(
    showUI: Boolean,
    controlsLayout: MediaControlsLayout,
    showOverlay: Boolean,
    onShowOverlayChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val transitionAlpha = animatedVisibilityScope?.transition?.animateFloat(
        transitionSpec = { MaterialTheme.motionScheme.defaultEffectsSpec() },
        label = "chromeAlpha"
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.PreEnter -> 0f
            androidx.compose.animation.EnterExitState.Visible -> 1f
            androidx.compose.animation.EnterExitState.PostExit -> 0f
        }
    }?.value ?: 1f

    val hasControls = controlsLayout != MediaControlsLayout.None
    val controlsWidth = Layout.PanelWidth
    val controlsEnterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val controlsExitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AnimatedVisibility(
        visible = showUI && hasControls,
        enter = fadeIn(animationSpec = controlsEnterEffects) +
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
        exit = fadeOut(animationSpec = controlsExitEffects) +
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = Spacing.Large)
            .graphicsLayer { alpha = transitionAlpha },
    ) {
        Box(
            modifier = Modifier.width(controlsWidth),
            contentAlignment = Alignment.Center,
        ) {
            MediaViewerControlButtonGroup(
                type = controlsLayout,
                showOverlay = showOverlay,
                onShowOverlayChange = onShowOverlayChange,
                onShowInfo = onShowInfo,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaViewerControlButtonGroup(
    type: MediaControlsLayout,
    showOverlay: Boolean,
    onShowOverlayChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
) {
    val groupModifier = when (type) {
        MediaControlsLayout.OverlayAndInfo -> Modifier.fillMaxWidth()
        MediaControlsLayout.OverlayOnly,
        MediaControlsLayout.InfoOnly,
        MediaControlsLayout.None -> Modifier.width(Layout.PanelWidth)
    }
    val groupArrangement = if (type == MediaControlsLayout.OverlayAndInfo) {
        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    } else {
        ButtonGroupDefaults.HorizontalArrangement
    }

    ButtonGroup(
        modifier = groupModifier,
        overflowIndicator = {
            ButtonGroupDefaults.OverflowIndicator(menuState = it)
        },
        horizontalArrangement = groupArrangement,
        expandedRatio = ButtonGroupDefaults.ExpandedRatio,
    ) {
        if (type == MediaControlsLayout.OverlayOnly || type == MediaControlsLayout.OverlayAndInfo) {
            toggleableItem(
                checked = showOverlay,
                label = "Overlay",
                onCheckedChange = onShowOverlayChange,
                weight = 1.1f,
                icon = {
                    Icon(
                        imageVector = if (showOverlay) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (showOverlay) "Hide overlay" else "Show overlay",
                    )
                },
            )
        }
        if (type == MediaControlsLayout.InfoOnly || type == MediaControlsLayout.OverlayAndInfo) {
            clickableItem(
                label = "Info",
                onClick = onShowInfo,
                weight = 1.0f,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Show info",
                    )
                },
            )
        }
    }
}
