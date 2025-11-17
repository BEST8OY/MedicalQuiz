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
import androidx.compose.material.icons.filled.Info
import com.medicalquiz.app.viewmodel.QuizViewModel
import com.medicalquiz.app.data.database.PerformanceFilter

@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    webViewStateFlow: kotlinx.coroutines.flow.MutableStateFlow<com.medicalquiz.app.utils.WebViewState>,
    mediaHandler: com.medicalquiz.app.ui.MediaHandler,
    filtersOnly: Boolean = false,
    onSubjectFilter: () -> Unit,
    onSystemFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onJumpTo: () -> Unit
    ,
    onStart: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showPerformanceDialog by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()

    android.util.Log.d("QuizRoot", "QuizRoot composing: filtersOnly=$filtersOnly")

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        scrimColor = androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Filter by Subject") },
                    selected = false,
                    onClick = {
                        onSubjectFilter()
                        scope.launch { drawerState.close() }
                        Unit
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Filter by System") },
                    selected = false,
                    onClick = {
                        onSystemFilter()
                        scope.launch { drawerState.close() }
                        Unit
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Filter by Performance") },
                    selected = false,
                    onClick = {
                        viewModel.openPerformanceDialog()
                        scope.launch { drawerState.close() }
                        Unit
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Clear Filters") },
                    selected = false,
                    onClick = {
                        onClearFilters()
                        scope.launch { drawerState.close() }
                        Unit
                    }
                )
                androidx.compose.material3.HorizontalDivider()
                NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = {
                    onSettings()
                    scope.launch { drawerState.close() }
                    Unit
                })
                NavigationDrawerItem(label = { Text("About") }, selected = false, onClick = {
                    onAbout()
                    scope.launch { drawerState.close() }
                    Unit
                })
            }
        }
    ) {
        // Add scrim click handler to close drawer
        if (drawerState.isOpen) {
            androidx.compose.foundation.background(
                color = androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
            )
            androidx.compose.foundation.clickable(
                enabled = drawerState.isOpen,
                onClick = { scope.launch { drawerState.close() } },
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            android.util.Log.d("QuizRoot", "Box rendering for Scaffold")
            // Top-level scaffold with topBar and bottomBar
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    android.util.Log.d("QuizRoot", "topBar lambda called: filtersOnly=$filtersOnly, should show=${!filtersOnly}")
                    if (!filtersOnly) {
                        android.util.Log.d("QuizRoot", "Rendering QuizTopBar")
                        QuizTopBar(
                            title = title,
                            subtitle = null,
                            onMenuClick = { scope.launch { drawerState.open() }; Unit },
                            onSettingsClick = onSettings
                        )
                    }
                },
                bottomBar = {
                    android.util.Log.d("QuizRoot", "bottomBar lambda called: filtersOnly=$filtersOnly, should show=${!filtersOnly}")
                    if (!filtersOnly) {
                        android.util.Log.d("QuizRoot", "Rendering QuizBottomBar")
                        QuizBottomBar(viewModel = viewModel, onJumpTo = onJumpTo)
                    }
                }
            ) { innerPadding ->
                QuizScreen(
                    viewModel = viewModel,
                    webViewStateFlow = webViewStateFlow,
                    mediaHandler = mediaHandler,
                    onPrevious = { viewModel.loadPrevious() },
                    onNext = { viewModel.loadNext() },
                    onJumpTo = onJumpTo,
                    onOpenSettings = onSettings,
                    onShowFilterSubject = onSubjectFilter,
                    onShowFilterSystem = onSystemFilter,
                    onSelectPerformance = { viewModel.openPerformanceDialog() },
                    onStart = onStart,
                    onClearFilters = onClearFilters,
                    filtersOnly = filtersOnly,
                    contentPadding = innerPadding
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                else -> {}
            }
        }
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(current = state.performanceFilter, onSelect = { selected ->
            viewModel.setPerformanceFilter(selected)
        }, onDismiss = { showPerformanceDialog = false })
    }
}
