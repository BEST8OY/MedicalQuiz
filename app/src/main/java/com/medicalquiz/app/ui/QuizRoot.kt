package com.medicalquiz.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.viewmodel.UiEvent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
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
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showPerformanceDialog by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                Divider()
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
            LaunchedEffect(viewModel) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                        else -> {}
                    }
                }
            }
        // Top-level scaffold with topBar and bottomBar
        Scaffold(
            topBar = if (filtersOnly) null else {
                QuizTopBar(
                    title = title,
                    subtitle = null,
                    onMenuClick = { scope.launch { drawerState.open() }; Unit },
                    onSettingsClick = onSettings
                )
            },
            bottomBar = {
                if (!filtersOnly) QuizBottomBar(viewModel = viewModel, onJumpTo = onJumpTo)
            }
        ) { innerPadding ->
            QuizScreen(
                viewModel = viewModel,
                webViewStateFlow = webViewStateFlow,
                mediaHandler = mediaHandler,
            contentPadding = innerPadding,
                onPrevious = { viewModel.loadPrevious() },
                onNext = { viewModel.loadNext() },
                onJumpTo = onJumpTo,
                onOpenSettings = onSettings,
                onShowFilterSubject = onSubjectFilter,
                onShowFilterSystem = onSystemFilter,
                onSelectPerformance = { viewModel.openPerformanceDialog() },
                onStart = { /* handled by activity */ }
            )
        }
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(current = viewModel.state.value.performanceFilter, onSelect = { selected ->
            viewModel.setPerformanceFilter(selected)
        }, onDismiss = { showPerformanceDialog = false })
    }
}
