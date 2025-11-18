package com.medicalquiz.app.ui

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.utils.WebViewState
import com.medicalquiz.app.viewmodel.QuizViewModel
import com.medicalquiz.app.viewmodel.UiEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    webViewStateFlow: MutableStateFlow<WebViewState>,
    mediaHandler: MediaHandler,
    onClearFilters: () -> Unit,
    onStart: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hostActivity = LocalActivity.current as? AppCompatActivity
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()
    val hasQuestions = state.questionIds.isNotEmpty() && state.currentQuestion != null
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    // Dialog states
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    // Event handling
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                is UiEvent.ShowErrorDialog -> errorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
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

    if (hasQuestions) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                NavigationDrawer(
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
                        onClearFilters()
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        showSettingsDialog = true
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
                    webViewStateFlow = webViewStateFlow,
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
            onStart = onStart,
            onClearFilters = onClearFilters
        )
    }

    // Dialogs
    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilterSilently(filter)
                viewModel.updatePreviewQuestionCount()
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
                viewModel.applySelectedSubjects(selected)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet())
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
                viewModel.applySelectedSystems(selected)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet())
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showResetLogsConfirmation) {
        ResetLogsConfirmationDialogComposable(
            isVisible = true,
            activity = hostActivity,
            onConfirm = { showResetLogsConfirmation = false },
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
    onSubjectFilter: () -> Unit,
    onSystemFilter: () -> Unit,
    onPerformanceFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Filters",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            NavigationDrawerItem(
                label = { Text("Subject") },
                icon = { Icon(Icons.Filled.Tune, null) },
                selected = false,
                onClick = onSubjectFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = { Text("System") },
                icon = { Icon(Icons.Filled.Storage, null) },
                selected = false,
                onClick = onSystemFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = { Text("Performance") },
                icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null) },
                selected = false,
                onClick = onPerformanceFilter,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            NavigationDrawerItem(
                label = { Text("Clear filters") },
                icon = { Icon(Icons.Filled.Refresh, null) },
                selected = false,
                onClick = onClearFilters,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Rounded.Settings, null) },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
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
                    subtitle = if (subjectCount == 0) "Tap to choose" else "$subjectCount selected",
                    icon = Icons.Filled.Tune,
                    onClick = onSelectSubjects
                )

                FilterSelectionCard(
                    title = "Systems",
                    subtitle = if (systemCount == 0) "Tap to choose" else "$systemCount selected",
                    icon = Icons.Filled.Storage,
                    onClick = onSelectSystems
                )

                FilterSelectionCard(
                    title = "Performance",
                    subtitle = performanceLabel,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    onClick = onSelectPerformance
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryActionButtons(
                hasPreview = hasPreview,
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
        previewCount > 1 -> "$previewCount questions ready"
        previewCount == 1 -> "1 question ready"
        else -> "No questions yet"
    }
    val supportingText = if (hasPreview) {
        "You're good to start whenever you're ready."
    } else {
        "Add at least one subject, system, or performance filter to see matching questions."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPreview) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
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
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButtons(
    hasPreview: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onStart,
            enabled = hasPreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasPreview) "Start quiz" else "Select filters to start")
        }
        OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text("Clear filters", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun formatPerformanceLabel(filter: com.medicalquiz.app.data.database.PerformanceFilter): String = when (filter) {
    com.medicalquiz.app.data.database.PerformanceFilter.ALL -> "All Questions"
    com.medicalquiz.app.data.database.PerformanceFilter.UNANSWERED -> "Not Attempted"
    com.medicalquiz.app.data.database.PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    com.medicalquiz.app.data.database.PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    com.medicalquiz.app.data.database.PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    com.medicalquiz.app.data.database.PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
