package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.Question

internal sealed interface MetadataSection {
    data class Chips(val label: String, val values: List<String>) : MetadataSection
}

internal fun computeMetadataSections(question: Question?): List<MetadataSection> {
    val currentQuestion = question ?: return emptyList()

    val sections = mutableListOf<MetadataSection>()
    sections += MetadataSection.Chips(label = "ID", values = listOf("#${currentQuestion.id}"))

    currentQuestion.difficultyTier?.let { tier ->
        val label = if (currentQuestion.pplTaken != null && currentQuestion.pplTaken > 0 && currentQuestion.corrTaken != null) {
            val rate = ((currentQuestion.corrTaken / currentQuestion.pplTaken) * 100).toInt()
            "${tier.displayName} ($rate% correct)"
        } else {
            tier.displayName
        }
        sections += MetadataSection.Chips(label = "Difficulty", values = listOf(label))
    }

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
internal fun QuestionMetadataCard(
    sections: List<MetadataSection>,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                MetadataTag(text = value)
            }
        }
    }
}

@Composable
private fun MetadataTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.heightIn(min = 24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun PerformanceCard(
    performance: QuestionPerformance?,
    modifier: Modifier = Modifier,
) {
    performance ?: return

    val contentColor = MaterialTheme.colorScheme.onSurface
    val lastResultColor = if (performance.lastCorrect)
        MaterialTheme.colorScheme.secondary
    else
        MaterialTheme.colorScheme.error

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
