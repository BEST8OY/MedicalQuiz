package com.medicalquiz.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.viewmodel.UiEvent
import com.medicalquiz.app.viewmodel.QuizViewModel
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalActivity
import com.medicalquiz.app.data.database.PerformanceFilter

@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    webViewStateFlow: kotlinx.coroutines.flow.MutableStateFlow<com.medicalquiz.app.utils.WebViewState>,
    mediaHandler: com.medicalquiz.app.ui.MediaHandler,
    onSubjectFilter: () -> Unit,
    onSystemFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onStart: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showPerformanceDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showJumpToDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showResetLogsConfirmation by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                // Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.foundation.background.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Medical Quiz",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Study Tools",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Filter Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "FILTERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Filter by Subject",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            onSubjectFilter()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Filter by System",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            onSystemFilter()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Filter by Performance",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            viewModel.openPerformanceDialog()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Clear All Filters",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            onClearFilters()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // App Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "APP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Settings",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            showSettingsDialog = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            android.util.Log.d("QuizRoot", "Box rendering for Scaffold")
            // Top-level scaffold with topBar and bottomBar
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    QuizTopBar(
                        title = title,
                        subtitle = null,
                        onMenuClick = { scope.launch { drawerState.open() } },
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
                    onShowFilterSubject = onSubjectFilter,
                    onShowFilterSystem = onSystemFilter,
                    onSelectPerformance = { viewModel.openPerformanceDialog() },
                    onStart = onStart,
                    onClearFilters = onClearFilters,
                    contentPadding = innerPadding
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                is UiEvent.ShowErrorDialog -> showErrorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
                else -> {}
            }
        }
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(current = state.performanceFilter, onSelect = { selected ->
            viewModel.setPerformanceFilterSilently(selected)
            viewModel.updatePreviewQuestionCount()
        }, onDismiss = { showPerformanceDialog = false })
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
        errorDialog = showErrorDialog,
        onDismiss = { showErrorDialog = null }
    )

    ResetLogsConfirmationDialogComposable(
        isVisible = showResetLogsConfirmation,
        activity = LocalActivity.current as? androidx.appcompat.app.AppCompatActivity,
        onConfirm = { showResetLogsConfirmation = false },
        onDismiss = { showResetLogsConfirmation = false }
    )
}
