package com.medqb.app.shared.ui.screens.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing

@Composable
internal fun FilterScreen(
    databaseName: String,
    subjectCount: Int,
    systemCount: Int,
    performanceFilter: PerformanceFilter,
    performanceLabel: String,
    previewCount: Int,
    isLoggingEnabled: Boolean,
    onLoggingToggle: (Boolean) -> Unit,
    submissionMode: SubmissionMode = SubmissionMode.INSTANT,
    onSubmissionModeToggle: (SubmissionMode) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    val hasPreview = previewCount > 0
    val hasFilters = subjectCount > 0 || systemCount > 0 || performanceFilter != PerformanceFilter.ALL

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = ScreenLayout.WideWidthBreakpoint)
                    .padding(
                        start = Inset.Large,
                        top = Inset.ExtraLarge,
                        end = Inset.Large,
                        bottom = Spacing.None,
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
                ) {
                    DatabaseHeaderCard(databaseName = databaseName)

                    FilterPreviewCard(previewCount = previewCount)

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FilterSelectionCard(
                                    title = "Subjects",
                                    subtitle = if (subjectCount == 0) "All subjects" else "$subjectCount selected",
                                    icon = Icons.Filled.Category,
                                    isActive = subjectCount > 0,
                                    onClick = onSelectSubjects
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                FilterSelectionCard(
                                    title = "Systems",
                                    subtitle = if (systemCount == 0) "All systems" else "$systemCount selected",
                                    icon = Icons.Filled.Layers,
                                    isActive = systemCount > 0,
                                    onClick = onSelectSystems
                                )
                            }
                        }

                        FilterSelectionCard(
                            title = "Performance",
                            subtitle = performanceLabel,
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isActive = performanceFilter != PerformanceFilter.ALL,
                            onClick = onSelectPerformance
                        )

                        ToggleCard(
                            icon = Icons.Filled.History,
                            title = "Track Session Progress",
                            description = "Record answer logs for historical tracking",
                            checked = isLoggingEnabled,
                            onCheckedChange = onLoggingToggle
                        )

                        ToggleCard(
                            icon = Icons.Filled.Edit,
                            title = "Manual Submission",
                            description = "Review your answer before submitting",
                            checked = submissionMode == SubmissionMode.MANUAL,
                            onCheckedChange = { checked ->
                                onSubmissionModeToggle(if (checked) SubmissionMode.MANUAL else SubmissionMode.INSTANT)
                            }
                        )
                    }

                    PrimaryActionButtonGroup(
                        hasPreview = hasPreview,
                        hasFilters = hasFilters,
                        onStart = onStart,
                        onClearFilters = onClearFilters
                    )

                    Spacer(modifier = Modifier.height(bottomContentPadding + Spacing.Medium))
                }
            }
        }
    }
}
