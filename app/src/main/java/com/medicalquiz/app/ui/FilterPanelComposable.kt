package com.medicalquiz.app.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StartFiltersPanel(
    modifier: Modifier = Modifier,
    selectedSubjectCount: Int,
    selectedChapterCount: Int,
    attemptFilterLabel: String,
    previewCountLabel: String,
    onSelectSubjects: () -> Unit,
    onSelectChapters: () -> Unit,
    onSelectAttemptFilter: () -> Unit,
    onPreview: () -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Quiz filters", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Fine-tune the next quiz. You can always adjust later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SummaryCluster(
            previewCountLabel = previewCountLabel,
            attemptFilterLabel = attemptFilterLabel,
            onPreview = onPreview
        )

        FilterCard(
            icon = Icons.Filled.CheckCircle,
            title = "Subjects",
            description = subjectLabel(selectedSubjectCount),
            actionLabel = "Pick subjects",
            onClick = onSelectSubjects
        )

        FilterCard(
            icon = Icons.Filled.CheckCircle,
            title = "Chapters",
            description = chapterLabel(selectedChapterCount),
            actionLabel = "Choose chapters",
            onClick = onSelectChapters
        )

        FilterCard(
            icon = Icons.Filled.Tune,
            title = "Attempts",
            description = attemptFilterLabel,
            actionLabel = "Filter attempts",
            onClick = onSelectAttemptFilter
        )

        ActionBar(onStart = onStart, onClear = onClear)
    }
}

@Composable
private fun SummaryCluster(
    previewCountLabel: String,
    attemptFilterLabel: String,
    onPreview: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ready to preview?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPreview, contentPadding = PaddingValues(0.dp)) {
                    Text("Preview quiz")
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryChip(label = "Queued", value = previewCountLabel)
                SummaryChip(label = "Attempts", value = attemptFilterLabel)
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Column(horizontalAlignment = Alignment.Start) {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        shape = MaterialTheme.shapes.large,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    )
}

@Composable
private fun FilterCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterIcon(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ActionBar(onStart: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp)
            )
            Text("Start quiz")
        }
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("Clear filters")
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun subjectLabel(count: Int) = when (count) {
    0 -> "No subjects selected"
    1 -> "1 subject selected"
    else -> "$count subjects selected"
}

private fun chapterLabel(count: Int) = when (count) {
    0 -> "No chapters selected"
    1 -> "1 chapter selected"
    else -> "$count chapters selected"
}