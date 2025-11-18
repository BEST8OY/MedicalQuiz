package com.medicalquiz.app.ui

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.FilterList
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                onShowFilterSubject = { showSubjectDialog = true },
                onShowFilterSystem = { showSystemDialog = true },
                onSelectPerformance = { viewModel.openPerformanceDialog() },
                onStart = onStart,
                onClearFilters = onClearFilters,
                contentPadding = padding
            )
        }
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
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
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
