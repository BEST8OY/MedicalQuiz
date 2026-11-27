package com.medqb.app.shared.ui.screens.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.adaptive.LocalAdaptiveLayoutInfo
import com.medqb.app.shared.ui.adaptive.WidthSizeClass
import com.medqb.app.shared.ui.components.ContentMaxWidth
import com.medqb.app.shared.ui.components.ResponsiveContentContainer

@Composable
internal fun FilterScreen(
    databaseName: String,
    subjectCount: Int,
    excludedSubjectCount: Int = 0,
    systemCount: Int,
    excludedSystemCount: Int = 0,
    statusFilter: StatusFilter,
    statusLabel: String,
    selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
    difficultyLabel: String = "All Difficulties",
    isDifficultyAvailable: Boolean? = null,
    previewCount: Int,
    isLoggingEnabled: Boolean,
    onLoggingToggle: (Boolean) -> Unit,
    submissionMode: SubmissionMode = SubmissionMode.INSTANT,
    onSubmissionModeToggle: (SubmissionMode) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectStatus: () -> Unit,
    onSelectDifficulty: () -> Unit = {},
    onStart: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPreview = previewCount > 0
    val isDifficultyActive = isDifficultyAvailable == true &&
        selectedDifficultyTiers.isNotEmpty() &&
        selectedDifficultyTiers.size < DifficultyTier.entries.size
    val hasFilters = subjectCount > 0 ||
        excludedSubjectCount > 0 ||
        systemCount > 0 ||
        excludedSystemCount > 0 ||
        !statusFilter.isAll ||
        isDifficultyActive

    val adaptive = LocalAdaptiveLayoutInfo.current
    val isTwoColumnFilterGrid = adaptive.widthClass == WidthSizeClass.Medium || adaptive.isLargeWidth
    val topPadding = if (adaptive.isCompactHeight) 16.dp else 32.dp
    val sectionSpacing = if (adaptive.isCompactHeight) 20.dp else 28.dp

    Surface(modifier = modifier.fillMaxSize()) {
        ResponsiveContentContainer(maxWidth = ContentMaxWidth.Standard) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        top = topPadding,
                        end = 24.dp,
                        bottom = 0.dp,
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                DatabaseHeaderCard(databaseName = databaseName)

                FilterPreviewCard(previewCount = previewCount)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isTwoColumnFilterGrid) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SubjectSystemFilterGroup(
                                subjectCount = subjectCount,
                                excludedSubjectCount = excludedSubjectCount,
                                systemCount = systemCount,
                                excludedSystemCount = excludedSystemCount,
                                onSelectSubjects = onSelectSubjects,
                                onSelectSystems = onSelectSystems,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )

                            StatusDifficultyFilterGroup(
                                statusFilter = statusFilter,
                                statusLabel = statusLabel,
                                difficultyLabel = difficultyLabel,
                                isDifficultyAvailable = isDifficultyAvailable,
                                isDifficultyActive = isDifficultyActive,
                                onSelectStatus = onSelectStatus,
                                onSelectDifficulty = onSelectDifficulty,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        SubjectSystemFilterGroup(
                            subjectCount = subjectCount,
                            excludedSubjectCount = excludedSubjectCount,
                            systemCount = systemCount,
                            excludedSystemCount = excludedSystemCount,
                            onSelectSubjects = onSelectSubjects,
                            onSelectSystems = onSelectSystems,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        StatusDifficultyFilterGroup(
                            statusFilter = statusFilter,
                            statusLabel = statusLabel,
                            difficultyLabel = difficultyLabel,
                            isDifficultyAvailable = isDifficultyAvailable,
                            isDifficultyActive = isDifficultyActive,
                            onSelectStatus = onSelectStatus,
                            onSelectDifficulty = onSelectDifficulty,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FilterToggleSegmentedItem(
                            icon = Icons.Filled.History,
                            title = "Track Session Progress",
                            description = "Record answer logs for historical tracking",
                            checked = isLoggingEnabled,
                            onCheckedChange = onLoggingToggle,
                            index = 0,
                            count = 2,
                        )

                        FilterToggleSegmentedItem(
                            icon = Icons.Filled.Edit,
                            title = "Manual Submission",
                            description = "Review your answer before submitting",
                            checked = submissionMode == SubmissionMode.MANUAL,
                            onCheckedChange = { checked ->
                                onSubmissionModeToggle(if (checked) SubmissionMode.MANUAL else SubmissionMode.INSTANT)
                            },
                            index = 1,
                            count = 2,
                        )
                    }
                }

                PrimaryActionButtonGroup(
                    hasPreview = hasPreview,
                    hasFilters = hasFilters,
                    onStart = onStart,
                    onClearFilters = onClearFilters
                )

                Spacer(modifier = Modifier.height(bottomContentPadding + 16.dp))
            }
        }
    }
}

@Composable
private fun SubjectSystemFilterGroup(
    subjectCount: Int,
    excludedSubjectCount: Int,
    systemCount: Int,
    excludedSystemCount: Int,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FilterSelectionSegmentedItem(
            title = "Subjects",
            subtitle = formatSelectionSubtitle(subjectCount, excludedSubjectCount, "subject", "subjects"),
            icon = Icons.Filled.Category,
            index = 0,
            count = 2,
            isActive = subjectCount > 0 || excludedSubjectCount > 0,
            onClick = onSelectSubjects,
        )

        FilterSelectionSegmentedItem(
            title = "Systems",
            subtitle = formatSelectionSubtitle(systemCount, excludedSystemCount, "system", "systems"),
            icon = Icons.Filled.Layers,
            index = 1,
            count = 2,
            isActive = systemCount > 0 || excludedSystemCount > 0,
            onClick = onSelectSystems,
        )
    }
}

@Composable
private fun StatusDifficultyFilterGroup(
    statusFilter: StatusFilter,
    statusLabel: String,
    difficultyLabel: String,
    isDifficultyAvailable: Boolean?,
    isDifficultyActive: Boolean,
    onSelectStatus: () -> Unit,
    onSelectDifficulty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FilterSelectionSegmentedItem(
            title = "Status",
            subtitle = statusLabel,
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            index = 0,
            count = 2,
            isActive = !statusFilter.isAll,
            onClick = onSelectStatus,
        )

        FilterSelectionSegmentedItem(
            title = "Difficulty",
            subtitle = if (isDifficultyAvailable == false) "Not available for this bank" else difficultyLabel,
            icon = Icons.Filled.FitnessCenter,
            index = 1,
            count = 2,
            isActive = isDifficultyActive,
            enabled = isDifficultyAvailable == true,
            // While availability is unknown (null, DB still opening), keep neutral styling
            // so available banks never flash from the dark disabled container color.
            showDisabledStyle = isDifficultyAvailable == false,
            onClick = onSelectDifficulty,
        )
    }
}
