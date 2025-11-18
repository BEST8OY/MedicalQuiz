package com.medicalquiz.app.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hostActivity = LocalContext.current as? AppCompatActivity

    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()

    val openSubjectFilter = {
        showSubjectDialog = true
    }
    val openSystemFilter = {
        showSystemDialog = true
    }

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
        if (showSubjectDialog) {
            viewModel.fetchSubjects()
        }
    }

    LaunchedEffect(showSystemDialog, state.selectedSubjectIds) {
        if (showSystemDialog) {
            val subjectsSnapshot = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            viewModel.fetchSystemsForSubjects(subjectsSnapshot)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            QuizDrawerContent(
                onSubjectFilter = {
                    openSubjectFilter()
                    scope.launch { drawerState.close() }
                },
                onSystemFilter = {
                    openSystemFilter()
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
                onOpenSettings = {
                    showSettingsDialog = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                QuizAppBar(
                    title = title,
                    questionIndex = state.currentQuestionIndex,
                    totalQuestions = state.totalQuestions,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onJumpToClick = { showJumpToDialog = true },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                QuizBottomBar(viewModel = viewModel, onJumpTo = { showJumpToDialog = true })
            }
        ) { innerPadding ->
            QuizScreen(
                viewModel = viewModel,
                webViewStateFlow = webViewStateFlow,
                mediaHandler = mediaHandler,
                onPrevious = { viewModel.loadPrevious() },
                onNext = { viewModel.loadNext() },
                onJumpTo = { showJumpToDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                onShowFilterSubject = openSubjectFilter,
                onShowFilterSystem = openSystemFilter,
                onSelectPerformance = { viewModel.openPerformanceDialog() },
                onStart = onStart,
                onClearFilters = onClearFilters,
                contentPadding = innerPadding
            )
        }
    }

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

    SettingsDialogComposable(
        isVisible = showSettingsDialog,
        viewModel = viewModel,
        onDismiss = { showSettingsDialog = false },
        onResetLogsRequested = { showResetLogsConfirmation = true }
    )

    JumpToDialogComposable(
        isVisible = showJumpToDialog,
        totalQuestions = state.questionIds.size,
        currentIndex = state.currentQuestionIndex,
        onJumpTo = { index ->
            viewModel.loadQuestion(index)
            showJumpToDialog = false
        },
        onDismiss = { showJumpToDialog = false }
    )

    ErrorDialogComposable(
        errorDialog = errorDialog,
        onDismiss = { errorDialog = null }
    )

    ResetLogsConfirmationDialogComposable(
        isVisible = showResetLogsConfirmation,
        activity = hostActivity,
        onConfirm = { showResetLogsConfirmation = false },
        onDismiss = { showResetLogsConfirmation = false }
    )

    SubjectFilterDialogComposable(
        isVisible = showSubjectDialog,
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

    SystemFilterDialogComposable(
        isVisible = showSystemDialog,
        resource = state.systemsResource,
        selectedIds = state.selectedSystemIds,
        onRetry = {
            val subjectsSnapshot = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            viewModel.fetchSystemsForSubjects(subjectsSnapshot)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizAppBar(
    title: String,
    questionIndex: Int,
    totalQuestions: Int,
    onMenuClick: () -> Unit,
    onJumpToClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(),
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(imageVector = Icons.Rounded.Menu, contentDescription = "Open navigation drawer")
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = if (title.isNotBlank()) title else "Medical Quiz", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (totalQuestions > 0) "${questionIndex + 1} / $totalQuestions" else "No questions loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onJumpToClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Jump to question")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Open settings")
            }
        }
    )
}

@Composable
private fun QuizDrawerContent(
    onSubjectFilter: () -> Unit,
    onSystemFilter: () -> Unit,
    onPerformanceFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick filters",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DrawerActionItem(
                label = "Filter by subject",
                icon = Icons.Filled.Tune,
                onClick = onSubjectFilter
            )
            DrawerActionItem(
                label = "Filter by system",
                icon = Icons.Filled.Storage,
                onClick = onSystemFilter
            )
            DrawerActionItem(
                label = "Performance filter",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                onClick = onPerformanceFilter
            )
            DrawerActionItem(
                label = "Clear all filters",
                icon = Icons.Filled.Refresh,
                onClick = onClearFilters
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            DrawerActionItem(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun DrawerActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    )
}
