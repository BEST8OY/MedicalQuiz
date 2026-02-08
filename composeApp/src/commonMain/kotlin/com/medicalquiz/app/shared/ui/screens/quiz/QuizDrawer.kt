package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.database.PerformanceFilter

@Composable
internal fun NavigationDrawer(
    subjectCount: Int = 0,
    systemCount: Int = 0,
    performanceFilter: PerformanceFilter = PerformanceFilter.ALL,
    onSubjectFilter: () -> Unit,
    onSystemFilter: () -> Unit,
    onPerformanceFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onSettings: () -> Unit,
    onChangeDatabase: () -> Unit
) {
    val hasActiveFilters = subjectCount > 0 || systemCount > 0 || performanceFilter != PerformanceFilter.ALL

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasActiveFilters) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subjects")
                        if (subjectCount > 0) {
                            FilterBadge(count = subjectCount)
                        }
                    }
                },
                icon = { Icon(Icons.Filled.Category, null) },
                selected = subjectCount > 0,
                onClick = onSubjectFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Systems")
                        if (systemCount > 0) {
                            FilterBadge(count = systemCount)
                        }
                    }
                },
                icon = { Icon(Icons.Filled.FilterAlt, null) },
                selected = systemCount > 0,
                onClick = onSystemFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Performance")
                        if (performanceFilter != PerformanceFilter.ALL) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null) },
                selected = performanceFilter != PerformanceFilter.ALL,
                onClick = onPerformanceFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            if (hasActiveFilters) {
                NavigationDrawerItem(
                    label = { Text("Clear all filters") },
                    icon = { Icon(Icons.Filled.FilterAltOff, null) },
                    selected = false,
                    onClick = onClearFilters,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Rounded.Settings, null) },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = { Text("Change Database") },
                icon = { Icon(Icons.Filled.FolderOpen, null) },
                selected = false,
                onClick = onChangeDatabase,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun FilterBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
