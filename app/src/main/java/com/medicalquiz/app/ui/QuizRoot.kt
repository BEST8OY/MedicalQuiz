package com.medicalquiz.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.viewmodel.UiEvent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.medicalquiz.app.viewmodel.QuizViewModel
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
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Medical Quiz",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Study Tools",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "FILTERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Filter by Subject")
                            }
                        },
                        selected = false,
                        onClick = {
                            onSubjectFilter()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Filter by System")
                            }
                        },
                        selected = false,
                        onClick = {
                            onSystemFilter()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Filter by Performance")
                            }
                        },
                        selected = false,
                        onClick = {
                            viewModel.openPerformanceDialog()
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Clear All Filters")
                            }
                        },
                        selected = false,
                        onClick = {
                            onClearFilters()
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // App Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "APP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.width(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Settings")
                            }
                        },
                        selected = false,
                        onClick = {
                            showSettingsDialog = true
                            scope.launch { drawerState.close() }
                        }
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
        activity = androidx.compose.ui.platform.LocalContext.current as? androidx.appcompat.app.AppCompatActivity,
        onConfirm = { showResetLogsConfirmation = false },
        onDismiss = { showResetLogsConfirmation = false }
    )
}
