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
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.ui.theme.AppTheme
import com.medicalquiz.app.shared.ui.screens.DatabaseSelectionScreen
import com.medicalquiz.app.shared.ui.screens.FilterHubScreen
import com.medicalquiz.app.shared.ui.screens.FilterPane
import com.medicalquiz.app.shared.ui.screens.media.HtmlViewerScreen
import com.medicalquiz.app.shared.ui.screens.SettingsScreen
import com.medicalquiz.app.shared.ui.screens.media.MediaViewerScreen
import com.medicalquiz.app.shared.ui.screens.quiz.QuizRoot
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.media.MediaType
import com.medicalquiz.app.shared.viewmodel.DatabaseSelectionViewModel
import com.medicalquiz.app.shared.viewmodel.FilterViewModel
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.SettingsViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent
import com.medicalquiz.app.shared.utils.MediaTypeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val START_DESTINATION: MedicalQuizRoutes = MedicalQuizRoutes.DatabaseSelection

private fun MutableList<MedicalQuizRoutes>.navigateTo(route: MedicalQuizRoutes) {
    if (lastOrNull() != route) {
        add(route)
    }
}

private fun MutableList<MedicalQuizRoutes>.navigateBack(): Boolean {
    if (size <= 1) return false
    removeLastOrNull()
    return true
}

private fun MutableList<MedicalQuizRoutes>.popToDatabaseSelection() {
    while (size > 1) {
        removeLastOrNull()
    }
}

private suspend fun navigateToMediaViewer(
    files: List<String>,
    startIndex: Int,
    backStack: MutableList<MedicalQuizRoutes>,
    mediaDescriptionsFlow: MutableStateFlow<Map<String, MediaDescription>>,
    localContentRepository: LocalContentRepository,
) {
    val availableFiles = mutableListOf<String>()
    for (fileName in files) {
        val isPlayableType = when (MediaTypeUtils.fromFileName(fileName)) {
            MediaType.IMAGE,
            MediaType.VIDEO,
            MediaType.AUDIO -> true
            else -> false
        }
        if (!isPlayableType) continue

        if (localContentRepository.mediaFileExists(fileName)) {
            availableFiles.add(fileName)
        }
    }

    if (availableFiles.isNotEmpty()) {
        val originalFile = files.getOrNull(startIndex)
        val newIndex = if (originalFile != null) {
            availableFiles.indexOf(originalFile).coerceAtLeast(0)
        } else 0
        val safeIndex = newIndex.coerceIn(0, availableFiles.lastIndex)

        val mediaDescriptions = withContext(Dispatchers.IO) {
            MediaDescriptionRepository.load()
        }
        mediaDescriptionsFlow.value = mediaDescriptions

        backStack.add(
            MedicalQuizRoutes.MediaViewer(
                files = availableFiles,
                startIndex = safeIndex,
            )
        )
    }
}

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
        val startupCoordinator = container.startupCoordinator
        val localContentRepository = container.localContentRepository
        val sessionRepository = container.sessionRepository

        val navRestoreBootstrap by produceState(
            initialValue = NavigationRestoreBootstrap(loaded = false, state = null),
            key1 = navStateRepo,
        ) {
            value = NavigationRestoreBootstrap(
                loaded = true,
                state = navStateRepo.restoreNavigationStateAsync(),
            )
        }

        if (!navRestoreBootstrap.loaded) {
            return@AppTheme
        }

        val (savedBackStack, savedDbName) = navRestoreBootstrap.state ?: (null to null)

        val backStack: SnapshotStateList<MedicalQuizRoutes> = remember {
            savedBackStack?.toMutableStateList() ?: mutableStateListOf(START_DESTINATION)
        }

        // Database state - restore from saved state or use null for fresh start
        var selectedDatabase by rememberSaveable { mutableStateOf<String?>(savedDbName) }
        var initializedDatabase by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingLaunchSource by rememberSaveable { mutableStateOf<QuizLaunchSource?>(null) }
        var requestedFilterPane by rememberSaveable { mutableStateOf<FilterPane?>(null) }
        var shouldAttemptSessionRestore by rememberSaveable {
            mutableStateOf(savedBackStack?.lastOrNull() is MedicalQuizRoutes.Quiz)
        }

        // Handle database initialization when selected
        LaunchedEffect(selectedDatabase, initializedDatabase, pendingLaunchSource, shouldAttemptSessionRestore) {
            val decision = startupCoordinator.handleDatabaseSelection(
                selectedDatabase = selectedDatabase,
                initializedDatabase = initializedDatabase,
                pendingLaunchSource = pendingLaunchSource,
                shouldAttemptSessionRestore = shouldAttemptSessionRestore
            )

            if (decision != null) {
                initializedDatabase = decision.initializedDatabase
                pendingLaunchSource = decision.pendingLaunchSource
                shouldAttemptSessionRestore = decision.shouldAttemptSessionRestore

                if (decision.shouldPopToDatabaseSelection) {
                    backStack.popToDatabaseSelection()
                }
            }
        }

        // Save navigation state to file whenever back stack changes
        LaunchedEffect(Unit) {
            snapshotFlow { backStack.toList() }
                .collect { currentStack ->
                    navPersistenceCoordinator.onBackStackChanged(
                        backStack = currentStack,
                        selectedDatabase = selectedDatabase,
                    )
                }
        }

        // Media descriptions state for viewer
        val mediaDescriptionsFlow = remember { MutableStateFlow<Map<String, MediaDescription>>(emptyMap()) }
        val snackbarHostState = remember { SnackbarHostState() }

        // Media handler with navigation callbacks
        val mediaHandler = remember {
            MediaHandler(
                onOpenMedia = { files, index ->
                    scope.launch {
                        navigateToMediaViewer(
                            files = files,
                            startIndex = index,
                            backStack = backStack,
                            mediaDescriptionsFlow = mediaDescriptionsFlow,
                            localContentRepository = localContentRepository,
                        )
                    }
                },
                onOpenHtml = { fileName ->
                    backStack.add(MedicalQuizRoutes.HtmlViewer(fileName = fileName))
                }
            )
        }

        // Handle navigation events from container's dispatcher
        LaunchedEffect(container.uiEventDispatcher) {
            container.uiEventDispatcher.events.collect { event ->
                when (event) {
                    is UiEvent.OpenHtmlFile -> {
                        backStack.add(MedicalQuizRoutes.HtmlViewer(fileName = event.fileName))
                    }
                    is UiEvent.OpenMedia -> {
                        navigateToMediaViewer(
                            files = event.urls,
                            startIndex = event.startIndex,
                            backStack = backStack,
                            mediaDescriptionsFlow = mediaDescriptionsFlow,
                            localContentRepository = localContentRepository,
                        )
                    }
                    is UiEvent.NavigateToDatabaseSelection -> {
                        backStack.popToDatabaseSelection()
                        selectedDatabase = null
                        initializedDatabase = null
                        container.activeDatabaseHolder.closeDatabase()
                        sessionRepository.clearSessionAsync()
                    }
                    is UiEvent.ShowSnackbar -> {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }

        val returnQuizToFilter: (Boolean) -> Unit = { launchedFromHistory ->
            pendingLaunchSource = null
            shouldAttemptSessionRestore = false
            requestedFilterPane = if (launchedFromHistory) {
                FilterPane.History
            } else {
                FilterPane.Filters
            }
            if (backStack.lastOrNull() is MedicalQuizRoutes.Quiz) {
                backStack.navigateBack()
            }
            if (backStack.lastOrNull() !is MedicalQuizRoutes.Filter) {
                backStack.popToDatabaseSelection()
                backStack.add(MedicalQuizRoutes.Filter)
            }
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
                            initializedDatabase = null
                            pendingLaunchSource = null
                            shouldAttemptSessionRestore = false
                            selectedDatabase = dbName
                            scope.launch {
                                sessionRepository.clearSessionAsync()
                            }
                            backStack.add(MedicalQuizRoutes.Filter)
                        },
                        onOpenSettings = {
                            backStack.navigateTo(MedicalQuizRoutes.Settings)
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
                    LaunchedEffect(requestedFilterPane) {
                        requestedFilterPane?.let { pane ->
                            selectedPane = pane
                            requestedFilterPane = null
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
                        onHistoryLaunchPrepared = { matchingDatabase, isLoggingEnabled ->
                            pendingLaunchSource = QuizLaunchSource.History
                            selectedDatabase = matchingDatabase
                            backStack.navigateTo(
                                MedicalQuizRoutes.Quiz(
                                    launchSource = QuizLaunchSource.History,
                                    isLoggingEnabled = isLoggingEnabled,
                                )
                            )
                        },
                        onStartQuiz = dropUnlessResumed {
                            val isLoggingEnabled = filterVM.state.value.isLoggingEnabled
                            backStack.add(
                                MedicalQuizRoutes.Quiz(isLoggingEnabled = isLoggingEnabled)
                            )
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
                entry<MedicalQuizRoutes.Quiz> { key ->
                    val quizVM = viewModel<QuizViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createQuizViewModel(
                                    savedStateHandle = createSavedStateHandle(),
                                    isLoggingEnabled = key.isLoggingEnabled,
                                )
                            }
                        }
                    )

                    LaunchedEffect(quizVM) {
                        if (quizVM.state.value.questionIds.isEmpty()) {
                            val restore = shouldAttemptSessionRestore || key.launchedFromHistory
                            if (restore) {
                                quizVM.restoreSession()
                                quizVM.loadFilteredQuestionIds(startFromBeginning = false)
                            } else {
                                quizVM.loadFilteredQuestionIds(startFromBeginning = true)
                            }
                            shouldAttemptSessionRestore = false
                        }
                    }

                    QuizRoot(
                        viewModel = quizVM,
                        mediaHandler = mediaHandler,
                        onNavigateBack = dropUnlessResumed {
                            quizVM.clearSession()
                            returnQuizToFilter(key.launchedFromHistory)
                        },
                        onOpenSettingsScreen = dropUnlessResumed {
                            backStack.navigateTo(MedicalQuizRoutes.Settings)
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
                        onBack = dropUnlessResumed { backStack.navigateBack() },
                        onResetLogs = {
                            val db = container.activeDatabaseHolder.databaseProvider.value
                            scope.launch {
                                try {
                                    db?.clearLogs()
                                    container.uiEventDispatcher.emitSnackbar("Logs cleared")
                                } catch (e: Exception) {
                                    container.uiEventDispatcher.emitSnackbar("Failed to clear logs: ${e.message}")
                                }
                            }
                        },
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
                        onBack = dropUnlessResumed {
                            backStack.navigateBack()
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
                            backStack.navigateBack()
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
                    val quizRoute = backStack.lastOrNull() as? MedicalQuizRoutes.Quiz
                    if (quizRoute != null) {
                        returnQuizToFilter(quizRoute.launchedFromHistory)
                    } else {
                        backStack.navigateBack()
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
    val state: Pair<List<MedicalQuizRoutes>, String?>?,
)

private data class FilterRouteHandlers(
    val onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    val onDeleteHistoryEntries: (Set<String>) -> Unit,
    val onRenameHistoryEntry: (String, String) -> Unit,
    val onStartQuiz: () -> Unit,
)

private fun buildFilterRouteHandlers(
    viewModel: FilterViewModel,
    onHistoryLaunchPrepared: (String, Boolean) -> Unit,
    onStartQuiz: () -> Unit,
): FilterRouteHandlers {
    return FilterRouteHandlers(
        onHistorySelected = { entry ->
            viewModel.restoreHistoryEntry(entry) { matchingDatabase ->
                onHistoryLaunchPrepared(matchingDatabase, entry.isLoggingEnabled)
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
