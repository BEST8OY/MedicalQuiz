package com.medqb.app.shared.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.utils.Resource

/**
 * Subject filter dialog.
 */
@Composable
fun SubjectFilterDialog(
    isVisible: Boolean,
    resource: Resource<List<Subject>>,
    selectedIds: Set<Long>,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        isVisible = isVisible,
        title = "Select subjects",
        resource = resource,
        emptyMessage = "No subjects available in this database.",
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        onRetry = onRetry,
        onApply = onApply,
        onClear = onClear,
        onDismiss = onDismiss
    )
}

/**
 * System filter dialog.
 */
@Composable
fun SystemFilterDialog(
    isVisible: Boolean,
    resource: Resource<List<System>>,
    selectedIds: Set<Long>,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        isVisible = isVisible,
        title = "Select systems",
        resource = resource,
        emptyMessage = "Select at least one subject first to see available systems.",
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        onRetry = onRetry,
        onApply = onApply,
        onClear = onClear,
        onDismiss = onDismiss
    )
}

/**
 * Generic selection dialog for filtering items.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SelectionDialog(
    isVisible: Boolean,
    title: String,
    resource: Resource<List<T>>,
    emptyMessage: String,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    DialogShell(
        onDismiss = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedContent(
            targetState = resource,
            transitionSpec = {
                fadeIn(animationSpec = fastEffectsSpec) togetherWith
                    fadeOut(animationSpec = fastEffectsSpec)
            },
            label = "selection_dialog_content"
        ) { currentResource ->
            when (currentResource) {
                Resource.Loading -> SelectionLoadingContent(title = title, onDismiss = onDismiss)
                is Resource.Error -> SelectionErrorContent(
                    title = title,
                    message = currentResource.message,
                    onRetry = onRetry,
                    onDismiss = onDismiss
                )
                is Resource.Success -> {
                    val data = currentResource.data
                    if (data.isEmpty()) {
                        SelectionEmptyContent(
                            title = title,
                            message = emptyMessage,
                            onDismiss = onDismiss
                        )
                    } else {
                        SelectionListContent(
                            title = title,
                            items = data,
                            selectedIds = selectedIds,
                            labelProvider = labelProvider,
                            idProvider = idProvider,
                            onApply = onApply,
                            onClear = onClear,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionLoadingContent(
    title: String,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(onClick = onDismiss) {
                    Text("Close")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SelectionEmptyContent(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun <T> SelectionListContent(
    title: String,
    items: List<T>,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember(selectedIds) {
        mutableStateOf(selectedIds.toMutableSet())
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val allIds = remember(items) { items.map { idProvider(it) }.toSet() }
    val isAllSelected = currentSelection.size == allIds.size && allIds.isNotEmpty()

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter {
            labelProvider(it).contains(searchQuery, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()

    Column {
        DialogHeader(
            title = title,
            subtitle = "${currentSelection.size} of ${items.size} selected",
            onClose = onDismiss
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            placeholder = { Text("Search...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Select all / Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { currentSelection = allIds.toMutableSet() },
                enabled = !isAllSelected
            ) {
                Text("Select all")
            }

            TextButton(
                onClick = { currentSelection = mutableSetOf() },
                enabled = currentSelection.isNotEmpty()
            ) {
                Text("Clear")
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Item list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                items = filteredItems,
                key = { idProvider(it) }
            ) { item ->
                val itemId = idProvider(item)
                val isChecked = currentSelection.contains(itemId)

                SelectionItem(
                    label = labelProvider(item),
                    isChecked = isChecked,
                    onCheckedChange = { checked ->
                        currentSelection = currentSelection.toMutableSet().apply {
                            if (checked) add(itemId) else remove(itemId)
                        }
                    }
                )
            }

            if (filteredItems.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DialogActions(
            primaryText = "Apply",
            onPrimary = { onApply(currentSelection.toSet()) },
            secondaryText = "Cancel",
            onSecondary = onDismiss
        )
    }
}

@Composable
private fun SelectionItem(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val backgroundColor = if (isChecked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!isChecked) },
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
