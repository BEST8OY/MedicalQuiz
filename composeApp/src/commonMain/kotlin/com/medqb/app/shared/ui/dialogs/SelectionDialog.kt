package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.ui.dialogs.components.LocalDialogCompactMode
import com.medqb.app.shared.ui.theme.ContainerSize
import com.medqb.app.shared.ui.theme.DialogLayout
import com.medqb.app.shared.ui.theme.IconSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.utils.Resource

enum class ItemFilterState {
    NEUTRAL,
    INCLUDED,
    EXCLUDED;

    fun next(): ItemFilterState = when (this) {
        NEUTRAL -> INCLUDED
        INCLUDED -> EXCLUDED
        EXCLUDED -> NEUTRAL
    }
}

/**
 * Selection dialog for subjects filter.
 */
@Composable
fun SubjectFilterDialog(
    resource: Resource<List<Subject>>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long> = emptySet(),
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        title = "Select subjects",
        resource = resource,
        selectedIds = selectedIds,
        excludedIds = excludedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        emptyMessage = "No subjects found",
        onApply = onApply,
        onRetry = onRetry,
        onDismiss = onDismiss
    )
}

/**
 * Selection dialog for systems filter.
 */
@Composable
fun SystemFilterDialog(
    resource: Resource<List<System>>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long> = emptySet(),
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialog(
        title = "Select systems",
        resource = resource,
        selectedIds = selectedIds,
        excludedIds = excludedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        emptyMessage = "No systems found",
        onApply = onApply,
        onRetry = onRetry,
        onDismiss = onDismiss
    )
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    resource: Resource<List<T>>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    emptyMessage: String,
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    DialogShell(onDismiss = onDismiss) {
        DialogHeader(title = title, onClose = onDismiss)

        when (resource) {
            Resource.Loading -> SelectionLoadingBody()
            is Resource.Error -> SelectionErrorBody(
                message = resource.message,
                onRetry = onRetry,
                onDismiss = onDismiss
            )
            is Resource.Success -> {
                val data = resource.data
                if (data.isEmpty()) {
                    SelectionEmptyBody(
                        message = emptyMessage,
                        onDismiss = onDismiss
                    )
                } else {
                    SelectionListContent(
                        items = data,
                        selectedIds = selectedIds,
                        excludedIds = excludedIds,
                        labelProvider = labelProvider,
                        idProvider = idProvider,
                        onApply = onApply,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.SelectionLoadingBody() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.LoadingAreaHeight)
            .padding(vertical = Spacing.ExtraLarge),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Large)
        ) {
            LoadingIndicator(
                modifier = Modifier.size(ContainerSize.Medium)
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
private fun ColumnScope.SelectionErrorBody(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.LoadingAreaHeight)
            .padding(Inset.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(ContainerSize.ExtraLarge)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.MediumLarge)
                )
            }
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.Medium)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
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
private fun ColumnScope.SelectionEmptyBody(
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.LoadingAreaHeight)
            .padding(Inset.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = Spacing.Large)
        )

        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
private fun <T> ColumnScope.SelectionListContent(
    items: List<T>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIncluded by remember(selectedIds) {
        mutableStateOf(selectedIds.toMutableSet())
    }
    var currentExcluded by remember(excludedIds) {
        mutableStateOf(excludedIds.toMutableSet())
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val isCompactHeight = LocalDialogCompactMode.current

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
    val isAllSelected = currentIncluded.size == effectiveSelectAllIds.size && currentExcluded.isEmpty() && effectiveSelectAllIds.isNotEmpty()

    val listState = rememberLazyListState()

    val subtitle = when {
        currentIncluded.isEmpty() && currentExcluded.isEmpty() -> "All items (none excluded)"
        currentIncluded.isNotEmpty() && currentExcluded.isEmpty() -> "${currentIncluded.size} of ${items.size} included"
        currentIncluded.isEmpty() && currentExcluded.isNotEmpty() -> "All except ${currentExcluded.size} excluded"
        else -> "${currentIncluded.size} included • ${currentExcluded.size} excluded"
    }

    val selectAllLabel = if (searchQuery.isNotBlank()) {
        "Select visible (${effectiveSelectAllIds.size})"
    } else {
        "Select all"
    }

    // Search Bar
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Inset.Large,
                vertical = if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.ExtraSmall
            ),
        placeholder = {
            Text(
                "Search...",
                style = if (isCompactHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (isCompactHeight) Modifier.size(IconSize.Small) else Modifier.size(IconSize.Medium)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { searchQuery = "" },
                    modifier = if (isCompactHeight) Modifier.size(IconSize.Large) else Modifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(IconSize.Small)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )

    // Help banner explaining 3-state tap
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Inset.Large, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "💡 Tap row to cycle:  [✓] Include  →  [✕] Exclude  →  Clear",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }

    // Consolidated Row: Subtitle (Left) + Actions (Right)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Inset.Large,
                vertical = if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.ExtraSmall
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            TextButton(
                onClick = {
                    currentIncluded = effectiveSelectAllIds.toMutableSet()
                    currentExcluded = mutableSetOf()
                },
                enabled = !isAllSelected,
                contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = 0.dp)
            ) {
                Text(selectAllLabel, style = MaterialTheme.typography.labelSmall)
            }

            TextButton(
                onClick = {
                    currentIncluded = mutableSetOf()
                    currentExcluded = mutableSetOf()
                },
                enabled = currentIncluded.isNotEmpty() || currentExcluded.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = 0.dp)
            ) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Inset.Large),
        color = MaterialTheme.colorScheme.outlineVariant
    )

    // Item list
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        contentPadding = PaddingValues(
            horizontal = Inset.Large,
            vertical = if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.MediumSmall
        ),
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) DialogLayout.CompactInputPadding else Spacing.ExtraSmall)
    ) {
        items(
            items = filteredItems,
            key = { idProvider(it) }
        ) { item ->
            val itemId = idProvider(item)
            val state = when {
                itemId in currentIncluded -> ItemFilterState.INCLUDED
                itemId in currentExcluded -> ItemFilterState.EXCLUDED
                else -> ItemFilterState.NEUTRAL
            }

            SelectionItem(
                label = labelProvider(item),
                state = state,
                compactMode = isCompactHeight,
                onClick = {
                    val next = state.next()
                    val newIncluded = currentIncluded.toMutableSet()
                    val newExcluded = currentExcluded.toMutableSet()
                    when (next) {
                        ItemFilterState.NEUTRAL -> {
                            newIncluded.remove(itemId)
                            newExcluded.remove(itemId)
                        }
                        ItemFilterState.INCLUDED -> {
                            newIncluded.add(itemId)
                            newExcluded.remove(itemId)
                        }
                        ItemFilterState.EXCLUDED -> {
                            newIncluded.remove(itemId)
                            newExcluded.add(itemId)
                        }
                    }
                    currentIncluded = newIncluded
                    currentExcluded = newExcluded
                }
            )
        }

        if (filteredItems.isEmpty() && searchQuery.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Inset.Large),
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
        onPrimary = { onApply(currentIncluded.toSet(), currentExcluded.toSet()) },
        secondaryText = "Cancel",
        onSecondary = onDismiss
    )
}

@Composable
private fun SelectionItem(
    label: String,
    state: ItemFilterState,
    compactMode: Boolean = false,
    onClick: () -> Unit,
) {
    val backgroundColor = when (state) {
        ItemFilterState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ItemFilterState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        ItemFilterState.NEUTRAL -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Inset.Small,
                    vertical = if (compactMode) DialogLayout.CompactItemPadding else Inset.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (state) {
                            ItemFilterState.INCLUDED -> MaterialTheme.colorScheme.primary
                            ItemFilterState.EXCLUDED -> MaterialTheme.colorScheme.error
                            ItemFilterState.NEUTRAL -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (state == ItemFilterState.NEUTRAL) 1.5.dp else 0.dp,
                        color = if (state == ItemFilterState.NEUTRAL) MaterialTheme.colorScheme.outline else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    ItemFilterState.INCLUDED -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Included",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    ItemFilterState.EXCLUDED -> Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Excluded",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(14.dp),
                    )
                    ItemFilterState.NEUTRAL -> Unit
                }
            }

            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.MediumSmall),
                style = if (compactMode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (state != ItemFilterState.NEUTRAL) FontWeight.SemiBold else FontWeight.Normal,
                color = when (state) {
                    ItemFilterState.INCLUDED -> MaterialTheme.colorScheme.onPrimaryContainer
                    ItemFilterState.EXCLUDED -> MaterialTheme.colorScheme.onErrorContainer
                    ItemFilterState.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            when (state) {
                ItemFilterState.INCLUDED -> {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "INCLUDE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = 2.dp),
                        )
                    }
                }
                ItemFilterState.EXCLUDED -> {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "EXCLUDE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = 2.dp),
                        )
                    }
                }
                ItemFilterState.NEUTRAL -> Unit
            }
        }
    }
}
