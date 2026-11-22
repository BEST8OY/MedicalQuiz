package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.medqb.app.shared.ui.components.SettingsActionButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TopBar(
    title: String,
    isMarked: Boolean = false,
    canMark: Boolean = false,
    onToggleMark: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onResetLogClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title.ifBlank { "MedQB" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        actions = {
            IconToggleButton(
                checked = isMarked,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleMark()
                },
                enabled = canMark,
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("quiz_mark_button")
            ) {
                AnimatedContent(
                    targetState = isMarked,
                    transitionSpec = {
                        val enter = if (targetState) {
                            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleIn(initialScale = 0.85f, animationSpec = motionScheme.fastSpatialSpec())
                        } else {
                            fadeIn(animationSpec = motionScheme.fastEffectsSpec())
                        }
                        enter.togetherWith(fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                    },
                    label = "QuizMarkAnimation"
                ) { marked ->
                    Icon(
                        imageVector = if (marked) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                        contentDescription = if (marked) "Unmark question" else "Mark question",
                    )
                }
            }
            IconButton(onClick = { showResetDialog = true }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset current question log",
                )
            }
            SettingsActionButton(
                onClick = onSettingsClick,
                contentDescription = "Settings",
            )
        },
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = "Reset Question Log?",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = "This will clear your previous attempts and statistics for this question. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetLogClick()
                    }
                ) {
                    Text(
                        text = "Reset",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
