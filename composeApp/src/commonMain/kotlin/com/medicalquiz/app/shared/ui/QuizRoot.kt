package com.medicalquiz.app.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onChangeDatabase: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()
    val isQuizMode = state.questionIds.isNotEmpty() && state.currentQuestion != null
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    val uriHandler = LocalUriHandler.current
    val linkHandler: (String) -> Unit = remember(mediaHandler, uriHandler) {
        { url ->
            val normalizedUrl = url.trim()
            if (normalizedUrl.isEmpty()) return@remember
            if (!mediaHandler.handleMediaLink(normalizedUrl)) {
                try {
                    uriHandler.openUri(normalizedUrl)
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    // Get Coil image loader for memory cache management.
    // We configure the singleton factory in App(), so always read via SingletonImageLoader.
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }

    // Dialog states
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    // Media Viewer State - use ArrayList for Saveable compatibility
    var mediaViewerFiles by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var mediaViewerIndex by rememberSaveable { mutableStateOf(0) }
    var mediaDescriptions by remember { mutableStateOf<Map<String, MediaDescription>>(emptyMap()) }
    
    // HTML Viewer State - standalone viewer for HTML files
    var htmlViewerFile by rememberSaveable { mutableStateOf<String?>(null) }

    val isOverlayVisible = showPerformanceDialog ||
        showSettingsDialog ||
        showJumpToDialog ||
        showSubjectDialog ||
        showSystemDialog ||
        showResetLogsConfirmation ||
        errorDialog != null ||
        mediaViewerFiles != null ||
        htmlViewerFile != null

    // In the pre-quiz filter screen, system back should return to DB selection.
    PlatformBackHandler(enabled = !isQuizMode && !isOverlayVisible, onBack = onChangeDatabase)

    // Load media descriptions only when the viewer opens, clear when closed to free memory
    LaunchedEffect(mediaViewerFiles) {
        if (mediaViewerFiles != null && mediaDescriptions.isEmpty()) {
            mediaDescriptions = com.medicalquiz.app.shared.data.MediaDescriptionRepository.load()
        } else if (mediaViewerFiles == null && mediaDescriptions.isNotEmpty()) {
            // Clear descriptions when viewer closes to free memory
            mediaDescriptions = emptyMap()
        }
    }

    // Event handling
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                is UiEvent.ShowErrorDialog -> errorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
                is UiEvent.OpenHtmlFile -> {
                    htmlViewerFile = event.fileName
                }
                is UiEvent.OpenMedia -> {
                    // Filter out unavailable media files
                    val availableFiles = event.urls.filter { fileName ->
                        val path = "${com.medicalquiz.app.shared.platform.StorageProvider.getAppStorageDirectory()}/media/$fileName"
                        com.medicalquiz.app.shared.platform.FileSystemHelper.exists(path)
                    }
                    if (availableFiles.isNotEmpty()) {
                        mediaViewerFiles = ArrayList(availableFiles)
                        // Adjust start index if original file was filtered out
                        val originalFile = event.urls.getOrNull(event.startIndex)
                        val newIndex = if (originalFile != null) availableFiles.indexOf(originalFile).coerceAtLeast(0) else 0
                        mediaViewerIndex = newIndex.coerceIn(0, availableFiles.lastIndex)
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(showSubjectDialog) {
        if (showSubjectDialog) viewModel.fetchSubjects()
    }

    LaunchedEffect(showSystemDialog, state.selectedSubjectIds) {
        if (showSystemDialog) {
            val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            viewModel.fetchSystemsForSubjects(subjects)
        }
    }

    if (mediaViewerFiles != null) {
        MediaViewerScreen(
            mediaFiles = mediaViewerFiles!!,
            startIndex = mediaViewerIndex,
            mediaDescriptions = mediaDescriptions,
            onLinkClick = linkHandler,
            onBack = { mediaViewerFiles = null }
        )
    } else if (htmlViewerFile != null) {
        HtmlViewerDialog(
            fileName = htmlViewerFile!!,
            onDismiss = { htmlViewerFile = null },
            onLinkClick = linkHandler
        )
    } else if (isQuizMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                NavigationDrawer(
                    subjectCount = state.selectedSubjectIds.size,
                    systemCount = state.selectedSystemIds.size,
                    performanceFilter = state.performanceFilter,
                    onSubjectFilter = {
                        showSubjectDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onSystemFilter = {
                        showSystemDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onPerformanceFilter = {
                        viewModel.openPerformanceDialog()
                        scope.launch { drawerState.close() }
                    },
                    onClearFilters = {
                        viewModel.applySelectedSubjects(emptySet())
                        viewModel.applySelectedSystems(emptySet())
                        viewModel.setPerformanceFilter(PerformanceFilter.ALL, loadQuestions = true)
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        showSettingsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onChangeDatabase = {
                        onChangeDatabase()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopBar(
                        title = title,
                        questionIndex = state.currentQuestionIndex,
                        totalQuestions = state.totalQuestions,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onJumpClick = { showJumpToDialog = true },
                        onResetLogClick = { viewModel.clearCurrentQuestionLog() },
                        onSettingsClick = { showSettingsDialog = true }
                    )
                },
                bottomBar = {
                    QuizBottomBar(
                        viewModel = viewModel,
                        onJumpTo = { showJumpToDialog = true }
                    )
                }
            ) { padding ->
                QuizScreen(
                    viewModel = viewModel,
                    mediaHandler = mediaHandler,
                    onPrevious = { viewModel.loadPrevious() },
                    onNext = { viewModel.loadNext() },
                    onJumpTo = { showJumpToDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    contentPadding = padding
                )
            }
        }
    } else {
        FilterScreen(
            subjectCount = state.selectedSubjectIds.size,
            systemCount = state.selectedSystemIds.size,
            performanceLabel = performanceLabel,
            previewCount = state.previewQuestionCount,
            onSelectSubjects = { showSubjectDialog = true },
            onSelectSystems = { showSystemDialog = true },
            onSelectPerformance = { viewModel.openPerformanceDialog() },
            onStart = {
                viewModel.loadFilteredQuestionIds()
                viewModel.loadQuestion(0)
            },
            onClearFilters = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
            }
        )
    }

    // Dialogs
    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilter(filter, loadQuestions = isQuizMode)
                showPerformanceDialog = false
            },
            onDismiss = { showPerformanceDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialogComposable(
            isVisible = true,
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onResetLogsRequested = { showResetLogsConfirmation = true }
        )
    }

    if (showJumpToDialog) {
        JumpToDialogComposable(
            isVisible = true,
            totalQuestions = state.questionIds.size,
            currentIndex = state.currentQuestionIndex,
            onJumpTo = { index ->
                viewModel.loadQuestion(index)
                showJumpToDialog = false
            },
            onDismiss = { showJumpToDialog = false }
        )
    }

    if (showSubjectDialog) {
        SubjectFilterDialogComposable(
            isVisible = true,
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = { viewModel.fetchSubjects() },
            onApply = { selected ->
                viewModel.applySelectedSubjects(selected, loadQuestions = isQuizMode)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = isQuizMode)
                showSubjectDialog = false
            },
            onDismiss = { showSubjectDialog = false }
        )
    }

    if (showSystemDialog) {
        SystemFilterDialogComposable(
            isVisible = true,
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            onRetry = {
                val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                viewModel.fetchSystemsForSubjects(subjects)
            },
            onApply = { selected ->
                viewModel.applySelectedSystems(selected, loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet(), loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showResetLogsConfirmation) {
        ResetLogsConfirmationDialogComposable(
            isVisible = true,
            activity = null, // hostActivity
            onConfirm = {
                showResetLogsConfirmation = false
                viewModel.clearLogsFromDb()
            },
            onDismiss = { showResetLogsConfirmation = false }
        )
    }

    errorDialog?.let { (title, message) ->
        ErrorDialogComposable(
            errorDialog = title to message,
            onDismiss = { errorDialog = null }
        )
    }
}

private fun formatPerformanceLabel(filter: PerformanceFilter): String = when (filter) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
