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
import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.LocalContentRepository
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.data.MediaDescriptionRepository
import com.medicalquiz.app.shared.domain.ApplyFiltersUseCase
import com.medicalquiz.app.shared.domain.LoadQuestionUseCase
import com.medicalquiz.app.shared.domain.QuizSessionBoundaryUseCase
import com.medicalquiz.app.shared.domain.RestoreSessionUseCase
import com.medicalquiz.app.shared.domain.UiEventDispatcher
import com.medicalquiz.app.shared.navigation.NavigationStateRepository
import com.medicalquiz.app.shared.orchestration.AppNavigationPersistenceCoordinator
import com.medicalquiz.app.shared.orchestration.AppStartupCoordinator
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.UserDataManager
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.ui.theme.AppTheme
import com.medicalquiz.app.shared.ui.screens.DatabaseSelectionScreen
import com.medicalquiz.app.shared.ui.screens.FilterScreen
import com.medicalquiz.app.shared.ui.screens.media.HtmlViewerScreen
import com.medicalquiz.app.shared.ui.screens.SettingsScreen
import com.medicalquiz.app.shared.ui.screens.media.MediaViewerScreen
import com.medicalquiz.app.shared.ui.screens.quiz.QuizRoot
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.media.MediaType
import com.medicalquiz.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SubjectFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SystemFilterDialog
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.QuizViewModelDependencies
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

private fun MutableList<MedicalQuizRoutes>.resetToStartDestination() {
    clear()
    add(START_DESTINATION)
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
            LaunchedEffect(selectedDatabase, pendingLaunchSource, shouldAttemptSessionRestore) {
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
                            historyEntries = sessionHistory,
                            onDatabaseSelected = { dbName ->
                                selectedDatabase = dbName
                                // Navigate to filter screen after database selection
                                backStack.add(MedicalQuizRoutes.Filter)
                            },
                            onHistorySelected = { entry ->
                                scope.launch {
                                    val matchingDatabase = availableDatabases.firstOrNull {
                                        it.removeSuffix(".db") == entry.databaseName
                                    } ?: return@launch

                                    if (sessionRepository.restoreHistoryEntryAsync(entry.id) == null) {
                                        return@launch
                                    }

                                    viewModel.setLoadingState(true)
                                    pendingLaunchSource = QuizLaunchSource.History
                                    selectedDatabase = matchingDatabase
                                    backStack.resetToStartDestination()
                                    backStack.add(MedicalQuizRoutes.Quiz(launchSource = QuizLaunchSource.History))
                                }
                            },
                            onDeleteHistoryEntries = { entryIds ->
                                scope.launch {
                                    sessionRepository.deleteHistoryEntriesAsync(entryIds)
                                }
                            },
                            onRenameHistoryEntry = { entryId, newName ->
                                scope.launch {
                                    sessionRepository.renameHistoryEntryAsync(entryId, newName)
                                }
                            },
                            onOpenSettings = {
                                backStack.navigateTo(MedicalQuizRoutes.Settings)
                            },
                        )
                    }

                    // Filter Screen - pre-quiz configuration
                    entry<MedicalQuizRoutes.Filter> {
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        val performanceLabel = formatPerformanceLabel(state.performanceFilter)

                        // Dialog states - overlays within filter screen
                        var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
                        var showSystemDialog by rememberSaveable { mutableStateOf(false) }
                        var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }

                        // Load data when dialogs open
                        LaunchedEffect(showSubjectDialog) {
                            if (showSubjectDialog) viewModel.fetchSubjects()
                        }
                        LaunchedEffect(showSystemDialog, state.selectedSubjectIds) {
                            if (showSystemDialog) {
                                val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                                viewModel.fetchSystemsForSubjects(subjects)
                            }
                        }

                        FilterScreen(
                            databaseName = state.databaseName,
                            subjectCount = state.selectedSubjectIds.size,
                            systemCount = state.selectedSystemIds.size,
                            performanceLabel = performanceLabel,
                            previewCount = state.previewQuestionCount,
                            isStudyModeEnabled = state.isStudyModeEnabled,
                            onSelectSubjects = { showSubjectDialog = true },
                            onSelectSystems = { showSystemDialog = true },
                            onSelectPerformance = { showPerformanceDialog = true },
                            onStart = dropUnlessResumed {
                                viewModel.resetAnswerState()
                                viewModel.loadFilteredQuestionIds()
                                backStack.add(MedicalQuizRoutes.Quiz())
                            },
                            onToggleStudyMode = { enabled ->
                                viewModel.setStudyMode(enabled)
                            },
                            onClearFilters = {
                                viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                                viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
                                viewModel.setPerformanceFilter(com.medicalquiz.app.shared.data.database.PerformanceFilter.ALL, loadQuestions = false)
                                viewModel.setStudyMode(false)
                            }
                        )

                        // Dialogs - rendered as overlays
                        if (showSubjectDialog) {
                            SubjectFilterDialog(
                                isVisible = true,
                                resource = state.subjectsResource,
                                selectedIds = state.selectedSubjectIds,
                                onRetry = { viewModel.fetchSubjects() },
                                onApply = { selected ->
                                    viewModel.applySelectedSubjects(selected, loadQuestions = false)
                                    showSubjectDialog = false
                                },
                                onClear = {
                                    viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                                    showSubjectDialog = false
                                },
                                onDismiss = { showSubjectDialog = false }
                            )
                        }

                        if (showSystemDialog) {
                            SystemFilterDialog(
                                isVisible = true,
                                resource = state.systemsResource,
                                selectedIds = state.selectedSystemIds,
                                onRetry = {
                                    val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                                    viewModel.fetchSystemsForSubjects(subjects)
                                },
                                onApply = { selected ->
                                    viewModel.applySelectedSystems(selected, loadQuestions = false)
                                    showSystemDialog = false
                                },
                                onClear = {
                                    viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
                                    showSystemDialog = false
                                },
                                onDismiss = { showSystemDialog = false }
                            )
                        }

                        if (showPerformanceDialog) {
                            PerformanceFilterDialog(
                                current = state.performanceFilter,
                                onSelect = { filter ->
                                    viewModel.setPerformanceFilter(filter, loadQuestions = false)
                                    showPerformanceDialog = false
                                },
                                onDismiss = { showPerformanceDialog = false }
                            )
                        }
                    }

                    // Quiz Screen - main question display with navigation drawer
                    entry<MedicalQuizRoutes.Quiz> { key ->
                        QuizRoot(
                            viewModel = viewModel,
                            mediaHandler = mediaHandler,
                            onNavigateBack = dropUnlessResumed {
                                // User is intentionally exiting the quiz - clear the session
                                // so it won't be restored on next app launch
                                viewModel.clearSession()
                                // For history-launched quizzes, return to main history screen.
                                if (key.launchedFromHistory) {
                                    backStack.popToDatabaseSelection()
                                } else {
                                    // Standard quiz flow: return to Filter.
                                    backStack.navigateBack()
                                }
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
                                if (!mediaHandler.handleMediaLink(url)) {
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
                                if (!mediaHandler.handleMediaLink(url)) {
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
                    onBack = { backStack.navigateBack() },
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

/**
 * Formats the performance filter to a user-friendly label.
 */
@Composable
private fun formatPerformanceLabel(filter: PerformanceFilter): String = remember(filter) {
    when (filter) {
        PerformanceFilter.ALL -> "All Questions"
        PerformanceFilter.UNANSWERED -> "Not Attempted"
        PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
        PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
        PerformanceFilter.EVER_CORRECT -> "Ever Correct"
        PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
    }
}

private data class NavigationRestoreBootstrap(
    val loaded: Boolean,
    val state: Pair<List<MedicalQuizRoutes>, String?>?,
)
