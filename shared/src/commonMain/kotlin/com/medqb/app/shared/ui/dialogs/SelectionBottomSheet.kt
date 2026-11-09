package com.medqb.app.shared.ui.dialogs

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
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
 * Selection bottom sheet for subjects filter.
 */
@Composable
fun SubjectFilterBottomSheet(
    resource: Resource<List<Subject>>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long> = emptySet(),
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionBottomSheet(
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
 * Selection bottom sheet for systems filter.
 */
@Composable
fun SystemFilterBottomSheet(
    resource: Resource<List<System>>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long> = emptySet(),
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionBottomSheet(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionBottomSheet(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Crossfade(
            targetState = resource,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            label = "selection_sheet_state"
        ) { res ->
            when (res) {
                Resource.Loading -> SelectionLoadingBody()
                is Resource.Error -> SelectionErrorBody(
                    message = res.message,
                    onRetry = onRetry,
                    onDismiss = onDismiss
                )
                is Resource.Success -> {
                    val data = res.data
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
                            onDismiss = onDismiss,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        )
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
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionErrorBody(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
private fun <T> SelectionListContent(
    items: List<T>,
    selectedIds: Set<Long>,
    excludedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (included: Set<Long>, excluded: Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var currentIncluded by remember(selectedIds) {
        mutableStateOf(selectedIds.toMutableSet())
    }
    var currentExcluded by remember(excludedIds) {
        mutableStateOf(excludedIds.toMutableSet())
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

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
    val isAllSelected = effectiveSelectAllIds.isNotEmpty() &&
        effectiveSelectAllIds.all { it in currentIncluded }

    val listState = rememberLazyListState()

    val baseSubtitle = when {
        currentIncluded.isEmpty() && currentExcluded.isEmpty() -> "All items (none excluded)"
        currentIncluded.isNotEmpty() && currentExcluded.isEmpty() -> "${currentIncluded.size} of ${items.size} included"
        currentIncluded.isEmpty() && currentExcluded.isNotEmpty() -> "All except ${currentExcluded.size} excluded"
        else -> "${currentIncluded.size} included • ${currentExcluded.size} excluded"
    }
    val subtitle = if (searchQuery.isBlank()) baseSubtitle else "$baseSubtitle (${filteredItems.size} shown)"

    val selectAllLabel = if (searchQuery.isNotBlank()) {
        "Select visible (${effectiveSelectAllIds.size})"
    } else {
        "Select all"
    }

    Column(
        modifier = modifier
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    "Search...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
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
                .padding(horizontal = 24.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡 Tap row to cycle:  [✓] Include  →  [✕] Exclude  →  Clear",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Consolidated Row: Subtitle (Left) + Actions (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        if (searchQuery.isBlank()) {
                            currentIncluded = effectiveSelectAllIds.toMutableSet()
                            currentExcluded = mutableSetOf()
                        } else {
                            currentIncluded = (currentIncluded + effectiveSelectAllIds).toMutableSet()
                            currentExcluded = (currentExcluded - effectiveSelectAllIds).toMutableSet()
                        }
                    },
                    enabled = !isAllSelected,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(selectAllLabel, style = MaterialTheme.typography.labelSmall)
                }

                TextButton(
                    onClick = {
                        currentIncluded = mutableSetOf()
                        currentExcluded = mutableSetOf()
                    },
                    enabled = currentIncluded.isNotEmpty() || currentExcluded.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
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
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            itemsIndexed(
                items = filteredItems,
                key = { _, item -> idProvider(item) }
            ) { index, item ->
                val itemId = idProvider(item)
                val state = when {
                    itemId in currentIncluded -> ItemFilterState.INCLUDED
                    itemId in currentExcluded -> ItemFilterState.EXCLUDED
                    else -> ItemFilterState.NEUTRAL
                }

                SelectionItem(
                    label = labelProvider(item),
                    state = state,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = filteredItems.size),
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
                    },
                    modifier = Modifier.animateItem(),
                )
            }

            if (filteredItems.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
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

        // Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onApply(currentIncluded.toSet(), currentExcluded.toSet()) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionItem(
    label: String,
    state: ItemFilterState,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (state) {
        ItemFilterState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ItemFilterState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        ItemFilterState.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainer
    }

    val contentColor = when (state) {
        ItemFilterState.INCLUDED -> MaterialTheme.colorScheme.onPrimaryContainer
        ItemFilterState.EXCLUDED -> MaterialTheme.colorScheme.onErrorContainer
        ItemFilterState.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                stateDescription = when (state) {
                    ItemFilterState.INCLUDED -> "Included"
                    ItemFilterState.EXCLUDED -> "Excluded"
                    ItemFilterState.NEUTRAL -> "Not selected"
                }
            },
        colors = ListItemDefaults.segmentedColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
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
                        shape = MaterialTheme.shapes.extraSmall,
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
        },
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state != ItemFilterState.NEUTRAL) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when (state) {
                ItemFilterState.INCLUDED -> {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            text = "INCLUDE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                ItemFilterState.EXCLUDED -> {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            text = "EXCLUDE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                ItemFilterState.NEUTRAL -> Unit
            }
        }
    )
}
