package com.medqb.app.shared.ui.screens.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.SubmissionMode

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
    isDifficultyAvailable: Boolean = false,
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
    val isDifficultyActive = isDifficultyAvailable &&
        selectedDifficultyTiers.isNotEmpty() &&
        selectedDifficultyTiers.size < DifficultyTier.entries.size
    val hasFilters = subjectCount > 0 ||
        systemCount > 0 ||
        !statusFilter.isAll ||
        isDifficultyActive

    Surface(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideScreen = maxWidth >= 600.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(
                        start = 24.dp,
                        top = 40.dp,
                        end = 24.dp,
                        bottom = 0.dp,
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    DatabaseHeaderCard(databaseName = databaseName)

                    FilterPreviewCard(previewCount = previewCount)

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (isWideScreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
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

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
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
                                        subtitle = if (!isDifficultyAvailable) "Not available for this bank" else difficultyLabel,
                                        icon = Icons.Filled.FitnessCenter,
                                        index = 1,
                                        count = 2,
                                        isActive = isDifficultyActive,
                                        enabled = isDifficultyAvailable,
                                        onClick = onSelectDifficulty,
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
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

                            Column(
                                modifier = Modifier.fillMaxWidth(),
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
                                    subtitle = if (!isDifficultyAvailable) "Not available for this bank" else difficultyLabel,
                                    icon = Icons.Filled.FitnessCenter,
                                    index = 1,
                                    count = 2,
                                    isActive = isDifficultyActive,
                                    enabled = isDifficultyAvailable,
                                    onClick = onSelectDifficulty,
                                )
                            }
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
}

internal fun formatDifficultyLabel(selectedTiers: Set<DifficultyTier>): String {
    return when {
        selectedTiers.isEmpty() || selectedTiers.size == DifficultyTier.entries.size -> "All Difficulties"
        selectedTiers.size == 1 -> selectedTiers.first().displayName
        selectedTiers.size == 2 -> selectedTiers.sortedBy { it.ordinal }.joinToString(", ") { it.displayName }
        else -> "${selectedTiers.size} selected"
    }
}

internal fun formatSelectionSubtitle(
    includedCount: Int,
    excludedCount: Int,
    singular: String,
    plural: String,
): String {
    return when {
        includedCount == 0 && excludedCount == 0 -> "All $plural"
        includedCount > 0 && excludedCount == 0 -> {
            if (includedCount == 1) "1 $singular selected" else "$includedCount selected"
        }
        includedCount == 0 && excludedCount > 0 -> {
            if (excludedCount == 1) "All except 1 $singular" else "All except $excludedCount"
        }
        else -> "$includedCount selected • $excludedCount excluded"
    }
}
