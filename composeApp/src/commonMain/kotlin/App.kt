package com.medicalquiz.app.shared

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.compose.setSingletonImageLoaderFactory
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.LocalContentRepository
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.data.MediaDescriptionRepository
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.di.AppDependencyContainer
import com.medicalquiz.app.shared.domain.AppIntent
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.navigation.AppNavigator
import com.medicalquiz.app.shared.navigation.NavigationSnapshot
import com.medicalquiz.app.shared.orchestration.AppWorkflowState
import com.medicalquiz.app.shared.orchestration.RequestedFilterPane
import com.medicalquiz.app.shared.ui.theme.AppTheme
import com.medicalquiz.app.shared.ui.screens.DatabaseSelectionScreen
import com.medicalquiz.app.shared.ui.screens.FilterHubScreen
import com.medicalquiz.app.shared.ui.screens.FilterPane
import com.medicalquiz.app.shared.ui.screens.media.HtmlViewerScreen
import com.medicalquiz.app.shared.ui.screens.SettingsScreen
import com.medicalquiz.app.shared.ui.screens.media.MediaViewerScreen
import com.medicalquiz.app.shared.ui.screens.quiz.QuizRoot
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.viewmodel.DatabaseSelectionViewModel
import com.medicalquiz.app.shared.viewmodel.FilterViewModel
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val START_DESTINATION: MedicalQuizRoutes = MedicalQuizRoutes.DatabaseSelection

private val AppWorkflowStateSaver = Saver<AppWorkflowState, List<Any?>>(
    save = { state ->
        listOf(
            state.selectedDatabase,
            state.initializedDatabase,
            state.pendingLaunchSource?.name,
            state.activeQuizLaunchSource.name,
            state.requestedFilterPane?.name,
            state.shouldAttemptSessionRestore
        )
    },
    restore = { list ->
        AppWorkflowState(
            selectedDatabase = list[0] as String?,
            initializedDatabase = list[1] as String?,
            pendingLaunchSource = (list[2] as String?)?.let { QuizLaunchSource.valueOf(it) },
            activeQuizLaunchSource = QuizLaunchSource.valueOf(list[3] as String),
            requestedFilterPane = (list[4] as String?)?.let { RequestedFilterPane.valueOf(it) },
            shouldAttemptSessionRestore = list[5] as Boolean
        )
    }
)

@Composable
fun App() {
    // Install Coil's singleton ImageLoader once.
    var coilInstalled by remember { mutableStateOf(false) }
    if (!coilInstalled) {
        setSingletonImageLoaderFactory { context ->
            generateImageLoader(context)
        }
        SideEffect {
            coilInstalled = true
        }
    }

    AppTheme {
        val scope = rememberCoroutineScope()
        
        // Initialize the Composition Root / manual DI Container
        val container = remember(scope) { AppDependencyContainer(scope) }

        DisposableEffect(container.userDataManager) {
            onDispose {
                CoroutineScope(Dispatchers.IO).launch {
                    container.userDataManager.close()
                }
            }
        }

        val navStateRepo = container.navStateRepo
        val navPersistenceCoordinator = container.navPersistenceCoordinator
        val workflowCoordinator = container.workflowCoordinator
        val mediaNavCoordinator = container.mediaNavigationCoordinator
        val localContentRepository = container.localContentRepository
        val sessionRepository = container.sessionRepository

        val navRestoreBootstrap by produceState(
            initialValue = NavigationRestoreBootstrap(loaded = false, snapshot = null),
            key1 = navStateRepo,
        ) {
            value = NavigationRestoreBootstrap(
                loaded = true,
                snapshot = navStateRepo.restoreNavigationStateAsync(),
            )
        }

        if (!navRestoreBootstrap.loaded) {
            return@AppTheme
        }

        val snapshot = navRestoreBootstrap.snapshot
        val savedBackStack = snapshot?.routes
        val savedDbName = snapshot?.selectedDatabase
        val savedQuizLaunchSource = snapshot?.quizLaunchSource

        val backStack: SnapshotStateList<MedicalQuizRoutes> = remember {
            savedBackStack?.toMutableStateList() ?: mutableStateListOf(START_DESTINATION)
        }
        val navigator = remember(backStack) { AppNavigator(backStack) }

        var workflowState by rememberSaveable(stateSaver = AppWorkflowStateSaver) {
            mutableStateOf(
                workflowCoordinator.initialState(
                    savedBackStack = savedBackStack,
                    savedDatabaseName = savedDbName,
                    savedQuizLaunchSource = savedQuizLaunchSource ?: QuizLaunchSource.Standard
                )
            )
        }

        // Handle database initialization when selected
        LaunchedEffect(
            workflowState.selectedDatabase,
            workflowState.initializedDatabase,
            workflowState.pendingLaunchSource,
            workflowState.shouldAttemptSessionRestore
        ) {
            val decision = workflowCoordinator.handleDatabaseSelection(workflowState)
            if (decision != null) {
                workflowState = workflowCoordinator.applyDatabaseSelectionDecision(workflowState, decision)
                if (decision.shouldPopToDatabaseSelection) {
                    navigator.popToDatabaseSelection()
                }
            }
        }

        // Save navigation state to file whenever back stack changes
        LaunchedEffect(Unit) {
            snapshotFlow { Pair(backStack.toList(), workflowState) }
                .debounce(300)
                .collect { (currentStack, wState) ->
                    navPersistenceCoordinator.onBackStackChanged(
                        backStack = currentStack,
                        selectedDatabase = wState.selectedDatabase,
                        quizLaunchSource = wState.activeQuizLaunchSource,
                    )
                }
        }

        // Media descriptions state for viewer
        val mediaDescriptionsFlow = remember { MutableStateFlow<Map<String, MediaDescription>>(emptyMap()) }
        val snackbarHostState = remember { SnackbarHostState() }

        val navigateToMediaViewer: (List<String>, Int) -> Unit = { files, index ->
            scope.launch {
                val request = mediaNavCoordinator.resolveMediaViewerRequest(files, index)
                if (request != null) {
                    mediaDescriptionsFlow.value = request.mediaDescriptions
                    navigator.navigateTo(request.route)
                }
            }
        }

        // Media handler with navigation callbacks
        val mediaHandler = remember {
            MediaHandler(
                onOpenMedia = { files, index ->
                    navigateToMediaViewer(files, index)
                },
                onOpenHtml = { fileName ->
                    navigator.navigateTo(MedicalQuizRoutes.HtmlViewer(fileName = fileName))
                }
            )
        }

        // Handle navigation and snackbar events from coordinators/dispatchers
        LaunchedEffect(container.appIntentDispatcher, container.snackbarDispatcher) {
            launch {
                container.appIntentDispatcher.intents.collect { intent ->
                    when (intent) {
                        is AppIntent.OpenHtmlFile -> {
                            navigator.navigateTo(MedicalQuizRoutes.HtmlViewer(fileName = intent.fileName))
                        }
                        is AppIntent.OpenMedia -> {
                            navigateToMediaViewer(intent.urls, intent.startIndex)
                        }
                        is AppIntent.NavigateToDatabaseSelection -> {
                            navigator.popToDatabaseSelection()
                            workflowState = workflowCoordinator.databaseSelectionRequested(workflowState)
                            container.activeDatabaseHolder.closeDatabase()
                            sessionRepository.clearSessionAsync()
                        }
                    }
                }
            }
            launch {
                container.snackbarDispatcher.messages.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }
        }

        val returnQuizToFilter: () -> Unit = {
            workflowState = workflowCoordinator.quizReturnedToFilter(workflowState)
            navigator.returnQuizToFilter()
        }

        val entryProvider = remember(
            container,
            mediaHandler,
            mediaDescriptionsFlow,
            localContentRepository,
        ) {
            entryProvider<MedicalQuizRoutes> {
                // Database Selection Screen
                entry<MedicalQuizRoutes.DatabaseSelection> {
                    val dbVM = viewModel<DatabaseSelectionViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createDatabaseSelectionViewModel()
                            }
                        }
                    )
                    val databases by dbVM.availableDatabases.collectAsStateWithLifecycle()
                    val isLoading by dbVM.isLoading.collectAsStateWithLifecycle()

                    DatabaseSelectionScreen(
                        databases = databases,
                        isLoading = isLoading,
                        onRefreshDatabases = { dbVM.refreshDatabases() },
                        onDatabaseSelected = { dbName ->
                            workflowState = workflowCoordinator.databaseSelected(workflowState, dbName)
                            scope.launch {
                                sessionRepository.clearSessionAsync()
                            }
                            navigator.navigateTo(MedicalQuizRoutes.Filter)
                        },
                        onOpenSettings = {
                            navigator.navigateTo(MedicalQuizRoutes.Settings)
                        },
                    )
                }

                // Filter Screen - pre-quiz configurations
                entry<MedicalQuizRoutes.Filter> {
                    val filterVM = viewModel<FilterViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createFilterViewModel()
                            }
                        }
                    )
                    var selectedPane by rememberSaveable { mutableStateOf(FilterPane.Filters) }
                    LaunchedEffect(workflowState.requestedFilterPane) {
                        workflowState.requestedFilterPane?.let { pane ->
                            selectedPane = when (pane) {
                                RequestedFilterPane.Filters -> FilterPane.Filters
                                RequestedFilterPane.History -> FilterPane.History
                            }
                            filterVM.initializeAfterDatabaseSwitch()
                            workflowState = workflowCoordinator.filterPaneRequestConsumed(workflowState)
                        }
                    }

                    val filterState by filterVM.state.collectAsStateWithLifecycle()
                    val databaseName = filterState.databaseName
                    val sessionHistory by filterVM.historyEntries.collectAsStateWithLifecycle(emptyList())
                    val scopedHistoryEntries = remember(sessionHistory, databaseName) {
                        sessionHistory.filter { it.databaseName == databaseName }
                    }

                    val routeHandlers = buildFilterRouteHandlers(
                        viewModel = filterVM,
                        onHistoryLaunchPrepared = { matchingDatabase ->
                            workflowState = workflowCoordinator.historyLaunchPrepared(workflowState, matchingDatabase)
                            navigator.navigateTo(MedicalQuizRoutes.Quiz)
                        },
                        onStartQuiz = dropUnlessResumed {
                            workflowState = workflowCoordinator.standardQuizLaunchPrepared(workflowState)
                            navigator.navigateTo(MedicalQuizRoutes.Quiz)
                        },
                    )

                    FilterHubScreen(
                        viewModel = filterVM,
                        selectedPane = selectedPane,
                        onPaneSelected = { selectedPane = it },
                        historyEntries = scopedHistoryEntries,
                        onHistorySelected = routeHandlers.onHistorySelected,
                        onDeleteHistoryEntries = routeHandlers.onDeleteHistoryEntries,
                        onRenameHistoryEntry = routeHandlers.onRenameHistoryEntry,
                        onStartQuiz = routeHandlers.onStartQuiz,
                    )
                }

                // Quiz Screen - active quiz takings
                entry<MedicalQuizRoutes.Quiz> {
                    val quizVM = viewModel<QuizViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createQuizViewModel(
                                    createSavedStateHandle()
                                )
                            }
                        }
                    )

                    LaunchedEffect(quizVM) {
                        // Wait until the active database name is propagated to the view model
                        quizVM.state.first { it.databaseName.isNotEmpty() }
                        if (quizVM.state.value.questionIds.isEmpty()) {
                            val restore = workflowState.shouldAttemptSessionRestore || workflowState.activeQuizLaunchSource == QuizLaunchSource.History
                            quizVM.restoreSession()
                            if (restore) {
                                quizVM.loadFilteredQuestionIds(startFromBeginning = false, appendToHistory = false)
                            } else {
                                quizVM.setSessionId("")
                                quizVM.loadFilteredQuestionIds(startFromBeginning = true)
                            }
                            workflowState = workflowCoordinator.quizRestoreConsumed(workflowState)
                        }
                    }

                    QuizRoot(
                        viewModel = quizVM,
                        mediaHandler = mediaHandler,
                        onNavigateBack = dropUnlessResumed {
                            returnQuizToFilter()
                        },
                        onOpenSettingsScreen = dropUnlessResumed {
                            navigator.navigateTo(MedicalQuizRoutes.Settings)
                        }
                    )
                }

                // Settings Screen
                entry<MedicalQuizRoutes.Settings> {
                    val settingsVM = viewModel<SettingsViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createSettingsViewModel()
                            }
                        }
                    )

                    SettingsScreen(
                        viewModel = settingsVM,
                        onBack = dropUnlessResumed { navigator.navigateBack() },
                    )
                }

                // Media Viewer Screen
                entry<MedicalQuizRoutes.MediaViewer> { key ->
                    val mediaDescriptions by mediaDescriptionsFlow.collectAsStateWithLifecycle()
                    val fontScalePreference = container.settingsRepository.fontScalePreference
                        .collectAsStateWithLifecycle(null).value

                    MediaViewerScreen(
                        mediaFiles = key.files,
                        startIndex = key.startIndex,
                        mediaDescriptions = mediaDescriptions,
                        richTextScale = fontScalePreference ?: 1f,
                        resolveMediaFilePath = localContentRepository::mediaFilePath,
                        mediaFileExists = { fileName ->
                            localContentRepository.mediaFileExists(fileName)
                        },
                        resolveOverlayPaths = { files ->
                            localContentRepository.resolveOverlayPaths(files)
                        },
                        onLinkClick = { url ->
                            if (!mediaHandler.handleMediaLink(url)) {
                                // Handle external URLs if not media
                            }
                        },
                        onSaveMedia = { fileName ->
                            scope.launch {
                                val saveDir = com.medicalquiz.app.shared.platform.StorageProvider.getAppStorageDirectory() + "/saved_media"
                                val sanitizedName = fileName.substringAfterLast("/").substringAfterLast("\\")
                                val destPath = "$saveDir/$sanitizedName"
                                // JVM-only: java.io.File works because both targets (Android, Desktop) are JVM-based
                                if (!java.io.File(destPath).canonicalPath.startsWith(java.io.File(saveDir).canonicalPath)) {
                                    container.snackbarDispatcher.emitSnackbar("Invalid file name")
                                    return@launch
                                }
                                val sourcePath = localContentRepository.mediaFilePath(fileName)
                                val success = com.medicalquiz.app.shared.platform.FileSystemHelper.copyFile(sourcePath, destPath)
                                if (success) {
                                    container.snackbarDispatcher.emitSnackbar("Media saved to: $destPath")
                                } else {
                                    container.snackbarDispatcher.emitSnackbar("Failed to save media")
                                }
                            }
                        },
                        onBack = dropUnlessResumed {
                            navigator.navigateBack()
                            scope.launch {
                                mediaDescriptionsFlow.value = emptyMap()
                            }
                        }
                    )
                }

                // HTML Viewer Screen
                entry<MedicalQuizRoutes.HtmlViewer> { key ->
                    val htmlDocument by produceState<LocalContentRepository.HtmlDocumentResult?>(
                        initialValue = null,
                        key1 = key.fileName,
                    ) {
                        value = localContentRepository.loadHtmlDocument(key.fileName)
                    }

                    HtmlViewerScreen(
                        fileName = key.fileName,
                        htmlContent = htmlDocument?.sanitizedHtml,
                        fileExists = htmlDocument?.fileExists ?: true,
                        isLoading = htmlDocument == null,
                        onBack = dropUnlessResumed {
                            navigator.navigateBack()
                        },
                        onLinkClick = { url ->
                            if (!mediaHandler.handleMediaLink(url)) {
                                // Handle external URLs
                            }
                        }
                    )
                }
            }
        }

        Box {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (navigator.currentRoute is MedicalQuizRoutes.Quiz) {
                        returnQuizToFilter()
                    } else {
                        navigator.navigateBack()
                    }
                },
                entryProvider = entryProvider,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it })
                },
                popTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
                }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            )
        }
    }
}

private data class NavigationRestoreBootstrap(
    val loaded: Boolean,
    val snapshot: NavigationSnapshot?,
)

private data class FilterRouteHandlers(
    val onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    val onDeleteHistoryEntries: (Set<String>) -> Unit,
    val onRenameHistoryEntry: (String, String) -> Unit,
    val onStartQuiz: () -> Unit,
)

private fun buildFilterRouteHandlers(
    viewModel: FilterViewModel,
    onHistoryLaunchPrepared: (String) -> Unit,
    onStartQuiz: () -> Unit,
): FilterRouteHandlers {
    return FilterRouteHandlers(
        onHistorySelected = { entry ->
            viewModel.restoreHistoryEntry(entry) { matchingDatabase ->
                onHistoryLaunchPrepared(matchingDatabase)
            }
        },
        onDeleteHistoryEntries = { entryIds ->
            viewModel.deleteHistoryEntries(entryIds)
        },
        onRenameHistoryEntry = { entryId, newName ->
            viewModel.renameHistoryEntry(entryId, newName)
        },
        onStartQuiz = onStartQuiz,
    )
}
