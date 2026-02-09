package com.medicalquiz.app.shared

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.compose.setSingletonImageLoaderFactory
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.data.MediaDescriptionRepository
import com.medicalquiz.app.shared.navigation.NavigationStateRepository
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.UserDataManager
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.navigation.MedicalQuizRoutes
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.theme.AppTheme
import com.medicalquiz.app.shared.ui.theme.LocalFontSize
import com.medicalquiz.app.shared.ui.screens.DatabaseSelectionScreen
import com.medicalquiz.app.shared.ui.screens.FilterScreen
import com.medicalquiz.app.shared.ui.screens.media.HtmlViewerDialog
import com.medicalquiz.app.shared.ui.screens.media.MediaViewerScreen
import com.medicalquiz.app.shared.ui.screens.quiz.QuizRoot
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SubjectFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SystemFilterDialog
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper function to process media files and navigate to media viewer.
 * Filters unavailable files on background thread and calculates the correct start index.
 */
private suspend fun navigateToMediaViewer(
    files: List<String>,
    startIndex: Int,
    backStack: MutableList<MedicalQuizRoutes>,
    mediaDescriptionsFlow: MutableStateFlow<Map<String, MediaDescription>>
) {
    // Filter unavailable files on IO dispatcher to avoid blocking main thread
    val availableFiles = withContext(Dispatchers.IO) {
        files.filter { fileName ->
            val path = "${StorageProvider.getAppStorageDirectory()}/media/$fileName"
            FileSystemHelper.exists(path)
        }
    }
    
    if (availableFiles.isNotEmpty()) {
        val originalFile = files.getOrNull(startIndex)
        val newIndex = if (originalFile != null) {
            availableFiles.indexOf(originalFile).coerceAtLeast(0)
        } else 0
        val safeIndex = newIndex.coerceIn(0, availableFiles.lastIndex)

        // Load media descriptions in parallel
        val mediaDescriptions = withContext(Dispatchers.IO) {
            MediaDescriptionRepository.load()
        }
        mediaDescriptionsFlow.value = mediaDescriptions

        // Navigate to media viewer
        backStack.add(
            MedicalQuizRoutes.MediaViewer(
                files = availableFiles,
                startIndex = safeIndex
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
    val fontSize by settingsRepository.fontSize.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalFontSize provides fontSize.sp) {
        AppTheme {
            val viewModel = viewModel { QuizViewModel() }
            val cacheManager = remember { CacheManager() }
            val scope = rememberCoroutineScope()

            // User data manager for highlights and other personal data
            val userDataManager = remember { UserDataManager() }
            val textHighlightsRepository = remember { TextHighlightsRepository(userDataManager, scope) }

            // Quiz session repository for persisting quiz state across process death
            val sessionRepository = remember { QuizSessionRepository() }
            var sessionHistory by remember { mutableStateOf(sessionRepository.listHistory()) }

            // Media descriptions state for viewer
            val mediaDescriptionsFlow = remember { MutableStateFlow<Map<String, MediaDescription>>(emptyMap()) }

            // Navigation back stack with custom save/restore that filters out transient routes
            // MediaViewer and HtmlViewer are not persisted - users start fresh on those
            val backStackSaver = remember {
                listSaver<SnapshotStateList<MedicalQuizRoutes>, String>(
                    save = { list ->
                        // Filter out transient routes before saving
                        list.filter { route ->
                            route !is MedicalQuizRoutes.MediaViewer &&
                            route !is MedicalQuizRoutes.HtmlViewer
                        }.map { kotlinx.serialization.json.Json.encodeToString(it) }
                    },
                    restore = { savedList ->
                        savedList.mapNotNull { jsonString ->
                            try {
                                kotlinx.serialization.json.Json.decodeFromString<MedicalQuizRoutes>(jsonString)
                            } catch (e: Exception) {
                                null
                            }
                        }.toMutableStateList()
                    }
                )
            }

            // Navigation state management
            val navStateRepo = remember { NavigationStateRepository() }
            val savedState = remember { navStateRepo.restoreNavigationState() }
            val (savedBackStack, savedDbName) = savedState ?: (null to null)

            // Always use remember (not rememberSaveable) for backStack to avoid
            // stale state from Bundle when app data is cleared. We only persist
            // to file, not to Compose saveable state.
            val backStack: SnapshotStateList<MedicalQuizRoutes> = remember {
                // Validate saved state - must start with DatabaseSelection
                val validStack = savedBackStack?.takeIf { stack ->
                    stack.isNotEmpty() && stack.first() is MedicalQuizRoutes.DatabaseSelection
                }
                if (savedBackStack != null && validStack == null) {
                    navStateRepo.clearNavigationState()
                }
                validStack?.toMutableStateList()
                    ?: mutableStateListOf(MedicalQuizRoutes.DatabaseSelection)
            }

            // Database state - restore from saved state or use null for fresh start
            var selectedDatabase by rememberSaveable { mutableStateOf<String?>(savedDbName) }
            var initializedDatabase by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingHistoryRestoreToken by rememberSaveable { mutableStateOf(0) }
            var handledHistoryRestoreToken by rememberSaveable { mutableStateOf(0) }

            // Initialize common dependencies
            LaunchedEffect(Unit) {
                userDataManager.init()
                viewModel.setSettingsRepository(settingsRepository)
                viewModel.setTextHighlightsRepository(textHighlightsRepository)
                viewModel.setCacheManager(cacheManager)
                viewModel.setSessionRepository(sessionRepository)
            }

            // Handle database initialization when selected
            LaunchedEffect(selectedDatabase, pendingHistoryRestoreToken) {
                selectedDatabase?.let { dbName ->
                    val hasDatabaseManager = viewModel.getDatabaseManager() != null
                    if (initializedDatabase != dbName || !hasDatabaseManager) {
                        val dbPath = FileSystemHelper.getDatabasePath(dbName)
                        val databaseManager = DatabaseManager(dbPath)
                        databaseManager.init()

                        viewModel.setDatabaseManager(databaseManager)
                        viewModel.setDatabaseName(dbName.removeSuffix(".db"))
                        initializedDatabase = dbName
                    }

                    val hasPendingHistoryRestore = pendingHistoryRestoreToken != handledHistoryRestoreToken
                    if (hasPendingHistoryRestore) {
                        val sessionRestored = viewModel.restoreSession()
                        if (sessionRestored) {
                            viewModel.loadFilteredQuestionIds()
                        }
                        handledHistoryRestoreToken = pendingHistoryRestoreToken
                        return@LaunchedEffect
                    }

                    // If we're on the Quiz screen but questionIds is empty (app restart),
                    // restore quiz session from saved state
                    val currentState = viewModel.state.value
                    val isOnQuizScreen = backStack.lastOrNull() is MedicalQuizRoutes.Quiz
                    if (isOnQuizScreen && currentState.questionIds.isEmpty()) {
                        val sessionRestored = viewModel.restoreSession()
                        if (sessionRestored) {
                            // Session was restored, load the filtered questions and current question
                            viewModel.loadFilteredQuestionIds()
                        } else {
                            // No saved session or session was for different database,
                            // go back to filter screen
                            if (backStack.size > 1) {
                                backStack.removeRange(1, backStack.size)
                            }
                        }
                    }
                }
            }

            // Save navigation state to file whenever back stack changes
            LaunchedEffect(Unit) {
                snapshotFlow { backStack.toList() }
                    .collect { currentStack ->
                        navStateRepo.saveNavigationState(currentStack, selectedDatabase)
                        if (currentStack.lastOrNull() is MedicalQuizRoutes.DatabaseSelection) {
                            sessionHistory = sessionRepository.listHistory()
                        }
                    }
            }

            // Media handler with navigation callbacks
            val mediaHandler = remember {
                MediaHandler(
                    onOpenMedia = { files, index ->
                        scope.launch {
                            navigateToMediaViewer(files, index, backStack, mediaDescriptionsFlow)
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
                                mediaDescriptionsFlow = mediaDescriptionsFlow
                            )
                        }
                        is UiEvent.NavigateToDatabaseSelection -> {
                            // Pop back to DatabaseSelection for smooth back navigation animation
                            if (backStack.size > 1) {
                                backStack.removeRange(1, backStack.size)
                            }
                            selectedDatabase = null
                            initializedDatabase = null
                            viewModel.closeDatabase()
                            // Clear the quiz session since we're switching databases
                            sessionRepository.clearSession()
                        }
                        // Other events (OpenPerformanceDialog, ShowErrorDialog, ShowResetLogsConfirmation)
                        // are handled within QuizRoot as dialog overlays
                        else -> Unit
                    }
                }
            }

            // Navigation entry provider
            val entryProvider = remember(viewModel, mediaHandler, mediaDescriptionsFlow) {
                entryProvider<MedicalQuizRoutes> {
                    // Database Selection Screen - app entry point
                    entry<MedicalQuizRoutes.DatabaseSelection> {
                        DatabaseSelectionScreen(
                            historyEntries = sessionHistory,
                            onDatabaseSelected = { dbName ->
                                selectedDatabase = dbName
                                // Navigate to filter screen after database selection
                                backStack.add(MedicalQuizRoutes.Filter)
                            },
                            onHistorySelected = { entry ->
                                val matchingDatabase = FileSystemHelper.listDatabases().firstOrNull {
                                    it.removeSuffix(".db") == entry.databaseName
                                }

                                if (matchingDatabase != null) {
                                    val restoredEntry = sessionRepository.restoreHistoryEntry(entry.id)
                                    if (restoredEntry != null) {
                                        pendingHistoryRestoreToken += 1
                                        selectedDatabase = matchingDatabase
                                        backStack.clear()
                                        backStack.add(MedicalQuizRoutes.DatabaseSelection)
                                        backStack.add(MedicalQuizRoutes.Filter)
                                        backStack.add(MedicalQuizRoutes.Quiz)
                                    }
                                }
                            },
                            onDeleteHistoryEntry = { entryId ->
                                sessionRepository.deleteHistoryEntry(entryId)
                                sessionHistory = sessionRepository.listHistory()
                            }
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
                            onSelectSubjects = { showSubjectDialog = true },
                            onSelectSystems = { showSystemDialog = true },
                            onSelectPerformance = { showPerformanceDialog = true },
                            onStart = dropUnlessResumed {
                                viewModel.loadFilteredQuestionIds()
                                viewModel.loadQuestion(0)
                                backStack.add(MedicalQuizRoutes.Quiz)
                            },
                            onClearFilters = {
                                viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                                viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
                                viewModel.setPerformanceFilter(com.medicalquiz.app.shared.data.database.PerformanceFilter.ALL, loadQuestions = false)
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
                    entry<MedicalQuizRoutes.Quiz> {
                        QuizRoot(
                            viewModel = viewModel,
                            mediaHandler = mediaHandler,
                            onNavigateBack = dropUnlessResumed {
                                // User is intentionally exiting the quiz - clear the session
                                // so it won't be restored on next app launch
                                viewModel.clearSession()
                                // NavDisplay will pop Quiz from back stack, returning to Filter
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    // Media Viewer Screen - full-screen media display
                    entry<MedicalQuizRoutes.MediaViewer> { key ->
                        val mediaDescriptions by mediaDescriptionsFlow.collectAsStateWithLifecycle()

                        MediaViewerScreen(
                            mediaFiles = key.files,
                            startIndex = key.startIndex,
                            mediaDescriptions = mediaDescriptions,
                            onLinkClick = { url ->
                                if (!mediaHandler.handleMediaLink(url)) {
                                    // Handle external URLs if not media
                                }
                            },
                            onBack = dropUnlessResumed {
                                // Pop from back stack
                                backStack.removeLastOrNull()
                                // Clear media descriptions to free memory
                                scope.launch {
                                    mediaDescriptionsFlow.value = emptyMap()
                                }
                            }
                        )
                    }

                    // HTML Viewer Screen - HTML content display
                    entry<MedicalQuizRoutes.HtmlViewer> { key ->
                        HtmlViewerDialog(
                            fileName = key.fileName,
                            onDismiss = dropUnlessResumed {
                                backStack.removeLastOrNull()
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

            // NavDisplay with slide animations and predictive back support
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
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
