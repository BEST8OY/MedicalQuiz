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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onChangeDatabase: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()
    val isQuizMode = state.questionIds.isNotEmpty() && state.currentQuestion != null
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    // Dialog states
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    // Media Viewer State - use ArrayList for Saveable compatibility
    var mediaViewerFiles by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var mediaViewerIndex by rememberSaveable { mutableStateOf(0) }
    var mediaDescriptions by remember { mutableStateOf<Map<String, MediaDescription>>(emptyMap()) }

    // Load media descriptions
    LaunchedEffect(Unit) {
        mediaDescriptions = com.medicalquiz.app.shared.data.MediaDescriptionRepository.load()
    }

    // Event handling
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                is UiEvent.ShowErrorDialog -> errorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
                is UiEvent.OpenMedia -> {
                    mediaViewerFiles = ArrayList(event.urls)
                    mediaViewerIndex = event.startIndex
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(showSubjectDialog) {
        if (showSubjectDialog) viewModel.fetchSubjects()
    }

    LaunchedEffect(showSystemDialog, state.selectedSubjectIds) {
        if (showSystemDialog) {
            val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            viewModel.fetchSystemsForSubjects(subjects)
        }
    }

    if (mediaViewerFiles != null) {
        MediaViewerScreen(
            mediaFiles = mediaViewerFiles!!,
            startIndex = mediaViewerIndex,
            mediaDescriptions = mediaDescriptions,
            onBack = { mediaViewerFiles = null }
        )
    } else if (isQuizMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                NavigationDrawer(
                    subjectCount = state.selectedSubjectIds.size,
                    systemCount = state.selectedSystemIds.size,
                    performanceFilter = state.performanceFilter,
                    onSubjectFilter = {
                        showSubjectDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onSystemFilter = {
                        showSystemDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onPerformanceFilter = {
                        viewModel.openPerformanceDialog()
                        scope.launch { drawerState.close() }
                    },
                    onClearFilters = {
                        viewModel.applySelectedSubjects(emptySet())
                        viewModel.applySelectedSystems(emptySet())
                        viewModel.setPerformanceFilter(PerformanceFilter.ALL, loadQuestions = true)
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        showSettingsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onChangeDatabase = {
                        onChangeDatabase()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopBar(
                        title = title,
                        questionIndex = state.currentQuestionIndex,
                        totalQuestions = state.totalQuestions,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onJumpClick = { showJumpToDialog = true },
                        onSettingsClick = { showSettingsDialog = true }
                    )
                },
                bottomBar = {
                    QuizBottomBar(
                        viewModel = viewModel,
                        onJumpTo = { showJumpToDialog = true }
                    )
                }
            ) { padding ->
                QuizScreen(
                    viewModel = viewModel,
                    mediaHandler = mediaHandler,
                    onPrevious = { viewModel.loadPrevious() },
                    onNext = { viewModel.loadNext() },
                    onJumpTo = { showJumpToDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    contentPadding = padding
                )
            }
        }
    } else {
        FilterScreen(
            subjectCount = state.selectedSubjectIds.size,
            systemCount = state.selectedSystemIds.size,
            performanceLabel = performanceLabel,
            previewCount = state.previewQuestionCount,
            onSelectSubjects = { showSubjectDialog = true },
            onSelectSystems = { showSystemDialog = true },
            onSelectPerformance = { viewModel.openPerformanceDialog() },
            onStart = {
                viewModel.loadFilteredQuestionIds()
                viewModel.loadQuestion(0)
            },
            onClearFilters = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
            }
        )
    }

    // Dialogs
    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilter(filter, loadQuestions = isQuizMode)
                showPerformanceDialog = false
            },
            onDismiss = { showPerformanceDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialogComposable(
            isVisible = true,
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onResetLogsRequested = { showResetLogsConfirmation = true }
        )
    }

    if (showJumpToDialog) {
        JumpToDialogComposable(
            isVisible = true,
            totalQuestions = state.questionIds.size,
            currentIndex = state.currentQuestionIndex,
            onJumpTo = { index ->
                viewModel.loadQuestion(index)
                showJumpToDialog = false
            },
            onDismiss = { showJumpToDialog = false }
        )
    }

    if (showSubjectDialog) {
        SubjectFilterDialogComposable(
            isVisible = true,
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = { viewModel.fetchSubjects() },
            onApply = { selected ->
                viewModel.applySelectedSubjects(selected, loadQuestions = isQuizMode)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = isQuizMode)
                showSubjectDialog = false
            },
            onDismiss = { showSubjectDialog = false }
        )
    }

    if (showSystemDialog) {
        SystemFilterDialogComposable(
            isVisible = true,
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            onRetry = {
                val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                viewModel.fetchSystemsForSubjects(subjects)
            },
            onApply = { selected ->
                viewModel.applySelectedSystems(selected, loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet(), loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showResetLogsConfirmation) {
        ResetLogsConfirmationDialogComposable(
            isVisible = true,
            activity = null, // hostActivity
            onConfirm = {
                showResetLogsConfirmation = false
                viewModel.clearLogsFromDb()
            },
            onDismiss = { showResetLogsConfirmation = false }
        )
    }

    errorDialog?.let { (title, message) ->
        ErrorDialogComposable(
            errorDialog = title to message,
            onDismiss = { errorDialog = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    questionIndex: Int,
    totalQuestions: Int,
    onMenuClick: () -> Unit,
    onJumpClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(),
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu"
                )
            }
        },
        title = {
            Text(
                text = title.ifBlank { "Medical Quiz" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    )
}

@Composable
private fun NavigationDrawer(
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
                    .padding(horizontal = 28.dp, vertical = 16.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

@Composable
private fun FilterScreen(
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

private fun formatPerformanceLabel(filter: PerformanceFilter): String = when (filter) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
