package com.medqb.app.shared

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.polymorphic
import coil3.compose.setSingletonImageLoaderFactory
import com.medqb.app.shared.data.CacheManager
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.data.MediaDescriptionRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.di.AppDependencyContainer
import com.medqb.app.shared.domain.AppIntent
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.navigation.QuizLaunchSource
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.orchestration.AppWorkflowState
import com.medqb.app.shared.orchestration.RequestedFilterPane
import com.medqb.app.shared.ui.theme.AppTheme
import com.medqb.app.shared.ui.screens.DatabaseSelectionScreen
import com.medqb.app.shared.ui.screens.FilterHubScreen
import com.medqb.app.shared.ui.screens.FilterPane
import com.medqb.app.shared.ui.screens.HistoryScreen
import com.medqb.app.shared.ui.screens.media.HtmlViewerScreen
import com.medqb.app.shared.ui.screens.SettingsScreen
import com.medqb.app.shared.ui.screens.media.MediaViewerScreen
import com.medqb.app.shared.ui.screens.quiz.QuizRoot
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.viewmodel.DatabaseSelectionViewModel
import com.medqb.app.shared.viewmodel.FilterViewModel
import com.medqb.app.shared.viewmodel.HistoryViewModel
import com.medqb.app.shared.viewmodel.QuizViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val START_DESTINATION: MedQBRoutes = MedQBRoutes.DatabaseSelection

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(baseClass = NavKey::class) {
            subclass(serializer = MedQBRoutes.DatabaseSelection.serializer())
            subclass(serializer = MedQBRoutes.Filter.serializer())
            subclass(serializer = MedQBRoutes.History.serializer())
            subclass(serializer = MedQBRoutes.Quiz.serializer())
            subclass(serializer = MedQBRoutes.Settings.serializer())
            subclass(serializer = MedQBRoutes.MediaViewer.serializer())
            subclass(serializer = MedQBRoutes.HtmlViewer.serializer())
        }
    }
}

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
        val container = remember { AppDependencyContainer() }

        DisposableEffect(container.userDataManager) {
            onDispose {
                CoroutineScope(Dispatchers.IO).launch {
                    container.userDataManager.close()
                }
            }
        }

        val navPersistenceCoordinator = container.navPersistenceCoordinator
        val workflowCoordinator = container.workflowCoordinator
        val mediaNavCoordinator = container.mediaNavigationCoordinator
        val localContentRepository = container.localContentRepository
        val sessionRepository = container.sessionRepository

        val backStack = rememberNavBackStack(navConfig, START_DESTINATION)
        val navigator = remember(backStack) { AppNavigator(backStack) }

        var workflowState by rememberSaveable(stateSaver = AppWorkflowStateSaver) {
            mutableStateOf(workflowCoordinator.initialState())
        }

        // On process-death restore, if Quiz is on top, signal session restore
        LaunchedEffect(Unit) {
            if (backStack.lastOrNull() is MedQBRoutes.Quiz &&
                !workflowState.shouldAttemptSessionRestore
            ) {
                workflowState = workflowState.copy(shouldAttemptSessionRestore = true)
            }
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

        // Refresh history when returning to database selection or filter screen
        LaunchedEffect(backStack.toList()) {
            val currentStack = backStack.toList()
            navPersistenceCoordinator.onBackStackChanged(currentStack)
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
                    navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = fileName))
                }
            )
        }

        // Handle navigation and snackbar events from coordinators/dispatchers
        LaunchedEffect(container.appIntentDispatcher, container.snackbarDispatcher) {
            launch {
                container.appIntentDispatcher.intents.collect { intent ->
                    when (intent) {
                        is AppIntent.OpenHtmlFile -> {
                            navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = intent.fileName))
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
            val targetPane = workflowState.requestedFilterPane
            workflowState = workflowState.copy(requestedFilterPane = null)
            when (targetPane) {
                RequestedFilterPane.History -> navigator.returnQuizToHistory()
                else -> navigator.returnQuizToFilter()
            }
        }

        val entryProvider = remember(
            container,
            mediaHandler,
            mediaDescriptionsFlow,
            localContentRepository,
        ) {
            entryProvider<NavKey> {
                // Database Selection Screen
                entry<MedQBRoutes.DatabaseSelection> {
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
                            navigator.navigateTo(MedQBRoutes.Filter)
                        },
                        onOpenSettings = {
                            navigator.navigateTo(MedQBRoutes.Settings)
                        },
                    )
                }

                // Filter Screen - pre-quiz configurations
                entry<MedQBRoutes.Filter> {
                    val filterVM = viewModel<FilterViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createFilterViewModel()
                            }
                        }
                    )

                    val onStartQuiz = dropUnlessResumed {
                        workflowState = workflowCoordinator.standardQuizLaunchPrepared(workflowState)
                        navigator.navigateTo(MedQBRoutes.Quiz)
                    }

                    FilterHubScreen(
                        viewModel = filterVM,
                        selectedPane = FilterPane.Filters,
                        onPaneSelected = { pane ->
                            when (pane) {
                                FilterPane.Filters -> navigator.switchTo(MedQBRoutes.Filter)
                                FilterPane.History -> navigator.switchTo(MedQBRoutes.History)
                            }
                        },
                        onStartQuiz = onStartQuiz,
                        onLoggingToggle = { container.settingsRepository.setLoggingEnabled(it) },
                        onSubmissionModeToggle = { container.settingsRepository.setSubmissionMode(it) },
                    )
                }

                // History Screen - quiz history
                entry<MedQBRoutes.History> {
                    val historyVM = viewModel<HistoryViewModel>(
                        factory = viewModelFactory {
                            initializer {
                                container.createHistoryViewModel()
                            }
                        }
                    )

                    val databaseName by container.activeDatabaseHolder.databaseName.collectAsStateWithLifecycle()
                    val sessionHistory by historyVM.historyEntries.collectAsStateWithLifecycle()
                    val scopedHistoryEntries = remember(sessionHistory, databaseName) {
                        sessionHistory.filter { it.databaseName == databaseName }
                    }

                    val onHistorySelected = { entry: QuizSessionRepository.QuizSession ->
                        historyVM.restoreHistoryEntry(entry) { matchingDatabase ->
                            workflowState = workflowCoordinator.historyLaunchPrepared(workflowState, matchingDatabase)
                            navigator.navigateTo(MedQBRoutes.Quiz)
                        }
                    }

                    HistoryScreen(
                        historyEntries = scopedHistoryEntries,
                        onHistorySelected = onHistorySelected,
                        onDeleteHistoryEntries = { historyVM.deleteHistoryEntries(it) },
                        onRenameHistoryEntry = { id, name -> historyVM.renameHistoryEntry(id, name) },
                        onCopyAllQids = { historyVM.getQuestionIdsForHistoryEntries(it) },
                        selectedPane = FilterPane.History,
                        onPaneSelected = { pane ->
                            when (pane) {
                                FilterPane.Filters -> navigator.switchTo(MedQBRoutes.Filter)
                                FilterPane.History -> navigator.switchTo(MedQBRoutes.History)
                            }
                        },
                    )
                }

                // Quiz Screen - active quiz takings
                entry<MedQBRoutes.Quiz> {
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
                            navigator.navigateTo(MedQBRoutes.Settings)
                        }
                    )
                }

                // Settings Screen
                entry<MedQBRoutes.Settings> {
                    val showMetadata by container.settingsRepository.showMetadata
                        .collectAsStateWithLifecycle(true)
                    val fontScalePreference by container.settingsRepository.fontScalePreference
                        .collectAsStateWithLifecycle(null)

                    SettingsScreen(
                        showMetadata = showMetadata,
                        fontScalePreference = fontScalePreference,
                        onShowMetadataToggle = { container.settingsRepository.setShowMetadata(it) },
                        onFontScaleChange = { container.settingsRepository.setFontScalePreference(it) },
                        onBack = dropUnlessResumed { navigator.navigateBack() },
                    )
                }

                // Media Viewer Screen
                entry<MedQBRoutes.MediaViewer> { key ->
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
                                when (val result = localContentRepository.saveMediaFile(fileName)) {
                                    is LocalContentRepository.SaveMediaResult.Success ->
                                        container.snackbarDispatcher.emitSnackbar("Media saved to: ${result.destPath}")
                                    LocalContentRepository.SaveMediaResult.InvalidFileName ->
                                        container.snackbarDispatcher.emitSnackbar("Invalid file name")
                                    LocalContentRepository.SaveMediaResult.CopyFailed ->
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
                entry<MedQBRoutes.HtmlViewer> { key ->
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
                    if (navigator.currentRoute is MedQBRoutes.Quiz) {
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
