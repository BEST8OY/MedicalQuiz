package com.medicalquiz.app.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun FilterScreen(
    subjectCount: Int,
    systemCount: Int,
    performanceLabel: String,
    previewCount: Int,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    val hasPreview = previewCount > 0
    val hasFilters = subjectCount > 0 || systemCount > 0 || performanceLabel != "All Questions"

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FilterPreviewCard(previewCount = previewCount)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterSelectionCard(
                    title = "Subjects",
                    subtitle = if (subjectCount == 0) "All subjects" else "$subjectCount selected",
                    icon = Icons.Filled.Category,
                    isActive = subjectCount > 0,
                    onClick = onSelectSubjects
                )

                FilterSelectionCard(
                    title = "Systems",
                    subtitle = if (systemCount == 0) "All systems" else "$systemCount selected",
                    icon = Icons.Filled.FilterAlt,
                    isActive = systemCount > 0,
                    onClick = onSelectSystems
                )

                FilterSelectionCard(
                    title = "Performance",
                    subtitle = performanceLabel,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    isActive = performanceLabel != "All Questions",
                    onClick = onSelectPerformance
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryActionButtons(
                hasPreview = hasPreview,
                hasFilters = hasFilters,
                onStart = onStart,
                onClearFilters = onClearFilters
            )
        }
    }
}

@Composable
private fun FilterPreviewCard(previewCount: Int) {
    val hasPreview = previewCount > 0
    val statusText = when {
        previewCount > 1 -> "$previewCount questions available"
        previewCount == 1 -> "1 question available"
        else -> "No matching questions"
    }
    val supportingText = if (hasPreview) {
        "Tap Start to begin your quiz session."
    } else {
        "Try adjusting your filters to find questions."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPreview) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasPreview) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasPreview) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) contentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButtons(
    hasPreview: Boolean,
    hasFilters: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onStart,
            enabled = hasPreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasPreview) "Start Quiz" else "No questions match")
        }
        if (hasFilters) {
            OutlinedButton(
                onClick = onClearFilters,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.FilterAltOff, contentDescription = null)
                Text("Reset Filters", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
