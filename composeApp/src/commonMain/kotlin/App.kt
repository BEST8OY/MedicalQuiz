package com.medicalquiz.app.shared

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.sp
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
import com.medicalquiz.app.shared.domain.ApplyFiltersUseCase
import com.medicalquiz.app.shared.domain.LoadQuestionUseCase
import com.medicalquiz.app.shared.domain.QuizSessionBoundaryUseCase
import com.medicalquiz.app.shared.domain.RestoreSessionUseCase
import com.medicalquiz.app.shared.domain.UiEventDispatcher
import com.medicalquiz.app.shared.navigation.NavigationStateRepository
import com.medicalquiz.app.shared.orchestration.AppHistoryCoordinator
import com.medicalquiz.app.shared.orchestration.AppNavigationPersistenceCoordinator
import com.medicalquiz.app.shared.orchestration.AppStartupCoordinator
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.UserDataManager
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
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.QuizViewModelDependencies
import com.medicalquiz.app.shared.viewmodel.UiEvent
import com.medicalquiz.app.shared.utils.HtmlUtils
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

private fun htmlFileNameFromLink(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null

    val source = if (trimmed.startsWith("media://", ignoreCase = true)) {
        trimmed.drop("media://".length)
    } else {
        trimmed
    }
    val fileName = HtmlUtils.normalizeFileName(source)
    return fileName.takeIf { it.isNotBlank() && MediaTypeUtils.isHtml(it) }
}

private fun mediaFileNameFromLink(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null

    val source = if (trimmed.startsWith("media://", ignoreCase = true)) {
        trimmed.drop("media://".length)
    } else {
        trimmed
    }
    val fileName = HtmlUtils.normalizeFileName(source)
    if (fileName.isBlank()) return null

    val isPlayableType = when (MediaTypeUtils.fromFileName(fileName)) {
        MediaType.IMAGE,
        MediaType.VIDEO,
        MediaType.AUDIO -> true
        else -> false
    }
    return fileName.takeIf { isPlayableType }
}

private fun mediaLinkIndexInFiles(url: String, files: List<String>): Int? {
    val targetFileName = mediaFileNameFromLink(url) ?: return null
    return files.indexOfFirst { fileName ->
        HtmlUtils.normalizeFileName(fileName).equals(targetFileName, ignoreCase = true)
    }.takeIf { it >= 0 }
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

    val settingsRepository = remember { SettingsRepository() }
    val localContentRepository = remember { LocalContentRepository() }
    val restoreSessionUseCase = remember { RestoreSessionUseCase() }
    val applyFiltersUseCase = remember { ApplyFiltersUseCase() }
    val loadQuestionUseCase = remember { LoadQuestionUseCase() }
    val uiEventDispatcher = remember { UiEventDispatcher() }

    AppTheme {
            val cacheManager = remember { CacheManager() }
            val scope = rememberCoroutineScope()

            // User data manager for highlights and other personal data
            val userDataManager = remember { UserDataManager() }
            val textHighlightsRepository = remember { TextHighlightsRepository(userDataManager, scope) }

            DisposableEffect(userDataManager) {
                onDispose {
                    CoroutineScope(Dispatchers.IO).launch {
                        userDataManager.close()
                    }
                }
            }

            // Quiz session repository for persisting quiz state across process death
            val sessionRepository = remember { QuizSessionRepository() }
            val quizSessionBoundaryUseCase = remember(sessionRepository) {
                QuizSessionBoundaryUseCase(sessionRepository)
            }
            val quizViewModelDependencies = remember(
                quizSessionBoundaryUseCase,
                applyFiltersUseCase,
                loadQuestionUseCase,
                uiEventDispatcher,
            ) {
                QuizViewModelDependencies(
                    quizSessionBoundaryUseCase = quizSessionBoundaryUseCase,
                    applyFiltersUseCase = applyFiltersUseCase,
                    loadQuestionUseCase = loadQuestionUseCase,
                    uiEventDispatcher = uiEventDispatcher,
                )
            }
            val sessionHistory by sessionRepository.historyEntries.collectAsStateWithLifecycle(emptyList())
            var availableDatabases by remember { mutableStateOf<List<String>>(emptyList()) }
            var isDatabaseListLoading by remember { mutableStateOf(true) }

            val quizViewModelFactory = remember(
                settingsRepository,
                textHighlightsRepository,
                cacheManager,
                quizViewModelDependencies,
            ) {
                viewModelFactory {
                    initializer {
                        QuizViewModel(
                            settingsRepository = settingsRepository,
                            textHighlightsRepository = textHighlightsRepository,
                            cacheManager = cacheManager,
                            savedStateHandle = createSavedStateHandle(),
                            dependencies = quizViewModelDependencies,
                        )
                    }
                }
            }
            val viewModel = viewModel<QuizViewModel>(factory = quizViewModelFactory)

            LaunchedEffect(viewModel, textHighlightsRepository) {
                viewModel.rebindTextHighlightsRepository(textHighlightsRepository)
            }

            // Media descriptions state for viewer
            val mediaDescriptionsFlow = remember { MutableStateFlow<Map<String, MediaDescription>>(emptyMap()) }
            val snackbarHostState = remember { SnackbarHostState() }

            // Navigation state management
            val navStateRepo = remember { NavigationStateRepository() }
            val navPersistenceCoordinator = remember(navStateRepo, sessionRepository) {
                AppNavigationPersistenceCoordinator(navStateRepo, sessionRepository)
            }
            val historyCoordinator = remember(sessionRepository) {
                AppHistoryCoordinator(sessionRepository)
            }
            val startupCoordinator = remember(
                localContentRepository,
                sessionRepository,
                restoreSessionUseCase,
            ) {
                AppStartupCoordinator(
                    localContentRepository = localContentRepository,
                    sessionRepository = sessionRepository,
                    restoreSessionUseCase = restoreSessionUseCase,
                )
            }
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

            // Always use remember (not rememberSaveable) for backStack to avoid
            // stale state from Bundle when app data is cleared. We only persist
            // to file, not to Compose saveable state.
            val backStack: SnapshotStateList<MedicalQuizRoutes> = remember {
                savedBackStack?.toMutableStateList()
                    ?: mutableStateListOf(START_DESTINATION)
            }

            // Database state - restore from saved state or use null for fresh start
            var selectedDatabase by rememberSaveable { mutableStateOf<String?>(savedDbName) }
            var initializedDatabase by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingLaunchSource by rememberSaveable { mutableStateOf<QuizLaunchSource?>(null) }
            var requestedFilterPane by rememberSaveable { mutableStateOf<FilterPane?>(null) }
            var shouldAttemptSessionRestore by rememberSaveable {
                mutableStateOf(savedBackStack?.lastOrNull() is MedicalQuizRoutes.Quiz)
            }

            val refreshDatabases: () -> Unit = {
                scope.launch {
                    isDatabaseListLoading = true
                    availableDatabases = startupCoordinator.refreshDatabases()
                    isDatabaseListLoading = false
                }
            }

            LaunchedEffect(Unit) {
                isDatabaseListLoading = true
                availableDatabases = startupCoordinator.initializeApp(userDataManager)
                isDatabaseListLoading = false
            }

            // Handle database initialization when selected
            LaunchedEffect(selectedDatabase, initializedDatabase, pendingLaunchSource, shouldAttemptSessionRestore) {
                val decision = startupCoordinator.handleDatabaseSelection(
                    selectedDatabase = selectedDatabase,
                    initializedDatabase = initializedDatabase,
                    pendingLaunchSource = pendingLaunchSource,
                    shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                    viewModel = viewModel,
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

            // Handle navigation events from ViewModel
            LaunchedEffect(viewModel) {
                viewModel.uiEvents.collect { event ->
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
                            viewModel.closeDatabase()
                            sessionRepository.clearSessionAsync()
                        }
                        is UiEvent.ShowToast -> {
                            snackbarHostState.showSnackbar(event.message)
                        }
                    }
                }
            }

            // Navigation entry provider
            val returnQuizToFilter: (Boolean) -> Unit = { launchedFromHistory ->
                // Clear active session so pressing Start from Filter creates a new history entry,
                // while still preserving selected filters in UI state.
                viewModel.clearSession()
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
                viewModel,
                mediaHandler,
                mediaDescriptionsFlow,
                localContentRepository,
                availableDatabases,
                isDatabaseListLoading,
            ) {
                entryProvider<MedicalQuizRoutes> {
                    // Database Selection Screen - app entry point
                    entry<MedicalQuizRoutes.DatabaseSelection> {
                        DatabaseSelectionScreen(
                            databases = availableDatabases,
                            isLoading = isDatabaseListLoading,
                            onRefreshDatabases = refreshDatabases,
                            onDatabaseSelected = { dbName ->
                                initializedDatabase = null
                                pendingLaunchSource = null
                                shouldAttemptSessionRestore = false
                                selectedDatabase = dbName
                                scope.launch {
                                    sessionRepository.clearSessionAsync()
                                }
                                // Navigate to filter screen after database selection
                                backStack.add(MedicalQuizRoutes.Filter)
                            },
                            onOpenSettings = {
                                backStack.navigateTo(MedicalQuizRoutes.Settings)
                            },
                        )
                    }

                    // Filter Screen - pre-quiz configuration
                    entry<MedicalQuizRoutes.Filter> {
                        var selectedPane by rememberSaveable { mutableStateOf(FilterPane.Filters) }
                        LaunchedEffect(requestedFilterPane) {
                            requestedFilterPane?.let { pane ->
                                selectedPane = pane
                                requestedFilterPane = null
                            }
                        }
                        val databaseName = viewModel.state.collectAsStateWithLifecycle().value.databaseName
                        val scopedHistoryEntries = remember(sessionHistory, databaseName) {
                            sessionHistory.filter { it.databaseName == databaseName }
                        }
                        val routeHandlers = buildFilterRouteHandlers(
                            historyCoordinator = historyCoordinator,
                            availableDatabases = availableDatabases,
                            allHistoryEntries = sessionHistory,
                            viewModel = viewModel,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            onHistoryLaunchPrepared = { matchingDatabase ->
                                pendingLaunchSource = QuizLaunchSource.History
                                selectedDatabase = matchingDatabase
                                // Keep Filter under Quiz so predictive back returns directly to Filter.
                                backStack.navigateTo(MedicalQuizRoutes.Quiz(launchSource = QuizLaunchSource.History))
                            },
                            onStartQuiz = dropUnlessResumed {
                                viewModel.loadFilteredQuestionIds(startFromBeginning = true)
                                backStack.add(MedicalQuizRoutes.Quiz())
                            },
                        )

                        FilterHubScreen(
                            viewModel = viewModel,
                            selectedPane = selectedPane,
                            onPaneSelected = { selectedPane = it },
                            historyEntries = scopedHistoryEntries,
                            onHistorySelected = routeHandlers.onHistorySelected,
                            onDeleteHistoryEntries = routeHandlers.onDeleteHistoryEntries,
                            onRenameHistoryEntry = routeHandlers.onRenameHistoryEntry,
                            onStartQuiz = routeHandlers.onStartQuiz,
                        )
                    }

                    // Quiz Screen - main question display with navigation drawer
                    entry<MedicalQuizRoutes.Quiz> { key ->
                        QuizRoot(
                            viewModel = viewModel,
                            mediaHandler = mediaHandler,
                            onNavigateBack = dropUnlessResumed {
                                returnQuizToFilter(key.launchedFromHistory)
                            },
                            onOpenSettingsScreen = dropUnlessResumed {
                                backStack.navigateTo(MedicalQuizRoutes.Settings)
                            }
                        )
                    }


                    entry<MedicalQuizRoutes.Settings> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = dropUnlessResumed { backStack.navigateBack() },
                            onResetLogs = { viewModel.clearLogsFromDb() },
                        )
                    }

                    // Media Viewer Screen - full-screen media display
                    entry<MedicalQuizRoutes.MediaViewer> { key ->
                        val mediaDescriptions by mediaDescriptionsFlow.collectAsStateWithLifecycle()
                        val fontScalePreference = viewModel.settingsRepository.fontScalePreference
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
                                val htmlFileName = htmlFileNameFromLink(url)
                                val mediaIndex = mediaLinkIndexInFiles(url, key.files)
                                if (htmlFileName != null) {
                                    backStack.add(MedicalQuizRoutes.HtmlViewer(fileName = htmlFileName))
                                } else if (mediaIndex != null) {
                                    scope.launch {
                                        navigateToMediaViewer(
                                            files = key.files,
                                            startIndex = mediaIndex,
                                            backStack = backStack,
                                            mediaDescriptionsFlow = mediaDescriptionsFlow,
                                            localContentRepository = localContentRepository,
                                        )
                                    }
                                } else if (!mediaHandler.handleMediaLink(url)) {
                                    // Handle external URLs if not media
                                }
                            },
                            onBack = dropUnlessResumed {
                                // Pop from back stack
                                backStack.navigateBack()
                                // Clear media descriptions to free memory
                                scope.launch {
                                    mediaDescriptionsFlow.value = emptyMap()
                                }
                            }
                        )
                    }

                    // HTML Viewer Screen - HTML content display
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
                                val htmlFileName = htmlFileNameFromLink(url)
                                if (htmlFileName != null) {
                                    backStack.add(MedicalQuizRoutes.HtmlViewer(fileName = htmlFileName))
                                } else if (!mediaHandler.handleMediaLink(url)) {
                                    // Handle external URLs
                                }
                            }
                        )
                    }
                }
            }

            Box {
                // NavDisplay with slide animations and predictive back support
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
                        // Forward navigation: slide in from right, slide out to left
                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        // Back navigation: slide in from left, slide out to right
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = {
                        // Predictive back gesture (Android 13+): slide in from left, slide out to right
                        // Same as popTransitionSpec but used during gesture
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    }
                )

                SnackbarHost(hostState = snackbarHostState)
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
    historyCoordinator: AppHistoryCoordinator,
    availableDatabases: List<String>,
    allHistoryEntries: List<QuizSessionRepository.QuizSession>,
    viewModel: QuizViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onHistoryLaunchPrepared: (String) -> Unit,
    onStartQuiz: () -> Unit,
): FilterRouteHandlers {
    val onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit = { entry ->
        scope.launch {
            val matchingDatabase = historyCoordinator.restoreHistoryEntry(
                entry = entry,
                availableDatabases = availableDatabases,
            ) ?: return@launch

            viewModel.setLoadingState(true)
            onHistoryLaunchPrepared(matchingDatabase)
        }
    }

    val onDeleteHistoryEntries: (Set<String>) -> Unit = { entryIds ->
        scope.launch {
            if (entryIds.isEmpty()) {
                return@launch
            }

            runCatching {
                historyCoordinator.deleteHistoryEntriesWithLogs(
                    entryIds = entryIds,
                    allHistoryEntries = allHistoryEntries,
                    availableDatabases = availableDatabases,
                )
            }.onFailure {
                snackbarHostState.showSnackbar(
                    message = "Failed to delete history logs: ${it.message ?: "unknown error"}"
                )
            }
        }
    }

    val onRenameHistoryEntry: (String, String) -> Unit = { entryId, newName ->
        scope.launch {
            historyCoordinator.renameHistoryEntry(
                entryId = entryId,
                newName = newName,
            )
        }
    }

    return FilterRouteHandlers(
        onHistorySelected = onHistorySelected,
        onDeleteHistoryEntries = onDeleteHistoryEntries,
        onRenameHistoryEntry = onRenameHistoryEntry,
        onStartQuiz = onStartQuiz,
    )
}
