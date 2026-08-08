package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

internal sealed interface MetadataSection {
    data class Chips(val label: String, val values: List<String>) : MetadataSection
}

internal fun computeMetadataSections(question: Question?): List<MetadataSection> {
    val currentQuestion = question ?: return emptyList()

    val sections = mutableListOf<MetadataSection>()
    sections += MetadataSection.Chips(label = "ID", values = listOf("#${currentQuestion.id}"))

    extractMetadataList(currentQuestion.subName)
        .takeIf { it.isNotEmpty() }
        ?.let { values ->
            val label = if (values.size == 1) "Subject" else "Subjects"
            sections += MetadataSection.Chips(label, values)
        }

    extractMetadataList(currentQuestion.sysName)
        .takeIf { it.isNotEmpty() }
        ?.let { values ->
            val label = if (values.size == 1) "System" else "Systems"
            sections += MetadataSection.Chips(label, values)
        }

    return sections
}

private val metadataDelimiters = Regex("[,;\\n•]+")

private fun extractMetadataList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(metadataDelimiters)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
internal fun QuestionMetadataCard(sections: List<MetadataSection>) {
    if (sections.isEmpty()) return

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = Stroke.Thin,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            sections.forEach { section ->
                when (section) {
                    is MetadataSection.Chips -> MetadataChipGroupRow(section.label, section.values)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataChipGroupRow(label: String, values: List<String>) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            values.forEach { value ->
                MetadataTag(text = value)
            }
        }
    }
}

@Composable
private fun MetadataTag(text: String) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.height(Spacing.Large),
        contentPadding = PaddingValues(horizontal = Spacing.SubSmall),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
internal fun PerformanceCard(performance: QuestionPerformance?) {
    performance ?: return

    val contentColor = MaterialTheme.colorScheme.onSurface
    val lastResultColor = if (performance.lastCorrect)
        MaterialTheme.colorScheme.tertiary
    else
        MaterialTheme.colorScheme.error

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = Stroke.Thin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.MediumSmall),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerformanceStat(
                label = "Attempts",
                value = performance.attempts.toString(),
                color = contentColor
            )

            PerformanceStat(
                label = "Last",
                value = if (performance.lastCorrect) "✓" else "✗",
                color = lastResultColor
            )

            PerformanceStat(
                label = "Score",
                value = "${performance.correctCount}/${performance.correctCount + performance.incorrectCount}",
                color = contentColor
            )
        }
    }
}

@Composable
private fun PerformanceStat(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Micro)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
