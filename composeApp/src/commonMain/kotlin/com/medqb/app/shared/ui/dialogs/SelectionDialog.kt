package com.medqb.app.shared.ui.dialogs

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
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
import com.medqb.app.shared.utils.Resource
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.ui.theme.DialogLayout
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing

/**
 * Selection dialog for subjects filter.
 */
@Composable
fun SubjectFilterDialog(
    isVisible: Boolean,
    resource: Resource<List<Subject>>,
    selectedIds: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        isVisible = isVisible,
        title = "Select subjects",
        resource = resource,
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        emptyMessage = "No subjects found",
        onApply = onApply,
        onClear = onClear,
        onRetry = onRetry,
        onDismiss = onDismiss
    )
}

/**
 * Selection dialog for systems filter.
 */
@Composable
fun SystemFilterDialog(
    isVisible: Boolean,
    resource: Resource<List<System>>,
    selectedIds: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        isVisible = isVisible,
        title = "Select systems",
        resource = resource,
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        emptyMessage = "No systems found",
        onApply = onApply,
        onClear = onClear,
        onRetry = onRetry,
        onDismiss = onDismiss
    )
}

@Composable
private fun <T> SelectionDialog(
    isVisible: Boolean,
    title: String,
    resource: Resource<List<T>>,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    emptyMessage: String,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
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
        DialogHeader(title = title, onClose = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Crossfade(
                targetState = resource,
                animationSpec = fastEffectsSpec,
                label = "selection_dialog_crossfade"
            ) { currentResource ->
                when (currentResource) {
                    Resource.Loading -> SelectionLoadingBody()
                    is Resource.Error -> SelectionErrorBody(
                        message = currentResource.message,
                        onRetry = onRetry,
                        onDismiss = onDismiss
                    )
                    is Resource.Success -> {
                        val data = currentResource.data
                        if (data.isEmpty()) {
                            SelectionEmptyBody(
                                message = emptyMessage,
                                onDismiss = onDismiss
                            )
                        } else {
                            SelectionListBody(
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionLoadingBody() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            LoadingIndicator(
                modifier = Modifier.size(ElementSize.IconContainerMd)
            )
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionErrorBody(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Inset.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(ElementSize.IconContainerXl)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(ElementSize.IconMdLg)
                )
            }
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.Md)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md)
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

@Composable
private fun SelectionEmptyBody(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Inset.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = Spacing.Lg)
        )

        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
private fun <T> SelectionListBody(
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

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter {
            labelProvider(it).contains(searchQuery, ignoreCase = true)
        }
    }

    val effectiveSelectAllIds = remember(allIds, filteredItems, searchQuery) {
        if (searchQuery.isBlank()) allIds
        else filteredItems.map { idProvider(it) }.toSet()
    }
    val isAllSelected = currentSelection.size == effectiveSelectAllIds.size && effectiveSelectAllIds.isNotEmpty()

    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < DialogLayout.CompactHeightThreshold

        Column(modifier = Modifier.fillMaxSize()) {
            val subtitle = if (searchQuery.isBlank()) {
                "${currentSelection.size} of ${items.size} selected"
            } else {
                "${currentSelection.size} of ${items.size} selected (${filteredItems.size} shown)"
            }

            if (isCompactHeight) {
                // Compact Landscape Row: Subtitle + Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Inset.Lg, vertical = DialogLayout.CompactInputPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs)) {
                        TextButton(
                            onClick = { currentSelection = effectiveSelectAllIds.toMutableSet() },
                            enabled = !isAllSelected,
                            contentPadding = PaddingValues(horizontal = Spacing.Xs, vertical = 0.dp)
                        ) {
                            Text("Select all", style = MaterialTheme.typography.labelSmall)
                        }

                        TextButton(
                            onClick = { currentSelection = mutableSetOf() },
                            enabled = currentSelection.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = Spacing.Xs, vertical = 0.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Compact Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Inset.Lg, vertical = DialogLayout.CompactInputPadding),
                    placeholder = { Text("Search...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ElementSize.IconSm)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(ElementSize.IconLg)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(ElementSize.IconSm)
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
            } else {
                // Portrait Standard Layout
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Inset.Lg, vertical = Spacing.Xxs)
                )

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Inset.Lg),
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
                        .padding(horizontal = Inset.Lg, vertical = Spacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { currentSelection = effectiveSelectAllIds.toMutableSet() },
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
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Inset.Lg),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Item list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    horizontal = Spacing.Lg,
                    vertical = if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.Sm
                ),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.Xxs)
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
                        compactMode = isCompactHeight,
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
                                .padding(Inset.Lg),
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
}

@Composable
private fun SelectionItem(
    label: String,
    isChecked: Boolean,
    compactMode: Boolean = false,
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
                .padding(
                    horizontal = Inset.Sm,
                    vertical = if (compactMode) DialogLayout.CompactItemPadding else Inset.Sm
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.Sm),
                style = if (compactMode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                modifier = if (compactMode) Modifier.size(ElementSize.IconLg) else Modifier
            )
        }
    }
}
