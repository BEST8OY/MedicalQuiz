package com.medicalquiz.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.medicalquiz.app.ui.theme.MedicalQuizTheme
// Drawer gravity & view helpers removed — Compose handles drawer and layout
import androidx.lifecycle.Lifecycle
// ViewGroup and other view-specific helpers removed; Compose manages the layout
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.navigation.NavigationView
import com.medicalquiz.app.data.CacheManager
import com.medicalquiz.app.data.SettingsRepository
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Question
// MenuItem handled by Compose top bar if needed
// Activity now uses Compose for its UI; view binding removed
// Using Compose-based settings dialog instead of XML-based binding
import com.medicalquiz.app.ui.FilterDialogHandler
import com.medicalquiz.app.ui.MediaHandler
import com.medicalquiz.app.ui.QuizState
import com.medicalquiz.app.ui.QuizScreen
import com.medicalquiz.app.utils.HtmlUtils
import com.medicalquiz.app.utils.QuestionHtmlBuilder
import com.medicalquiz.app.utils.Resource
import com.medicalquiz.app.utils.WebViewController
import com.medicalquiz.app.utils.WebViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.firstMatching
import com.medicalquiz.app.utils.launchCatching
import com.medicalquiz.app.viewmodel.QuizViewModel
import com.medicalquiz.app.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.withContext

class QuizActivity : AppCompatActivity() {
    
    // Activity now uses Compose for its UI; view binding removed
    
    // ViewModel
    private val viewModel: QuizViewModel by viewModels()
    
    // UI Handlers
    private lateinit var mediaHandler: MediaHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    // Compose manages toolbar state through view model; remove `topBarTitle`/`topBarSubtitle`.
    private val webViewStateFlow = MutableStateFlow(WebViewState())
    
    // Repositories
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cacheManager: CacheManager
    
    // State
    private var startTime: Long = 0
    // Lifecycle Methods
    // ============================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display for this activity
        WindowCompat.enableEdgeToEdge(window)
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDark

        initializeComponents()
        validateAndSetupDatabase()
        observeViewModelState()
        restoreStateIfNeeded(savedInstanceState)
        setupBackPressHandler()
    }

    override fun onPause() {
        super.onPause()
        viewModel.flushLogsIfEnabledOnPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeDatabase()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveQuizState(outState)
    }

    // Drawer navigation is now handled inside the Compose `QuizRoot`.

    // ============================================================================
    // Initialization
    // ============================================================================

    private fun initializeComponents() {
        // Compose handles window insets and scrolling. No view-based insets required.

        settingsRepository = SettingsRepository(this)
        cacheManager = CacheManager()
        
        viewModel.setSettingsRepository(settingsRepository)
        viewModel.setCacheManager(cacheManager)

        val initialPerformance = settingsRepository.performanceFilter.value
        viewModel.setPerformanceFilter(initialPerformance)
        settingsRepository.setPerformanceFilter(initialPerformance)
    }

    private fun validateAndSetupDatabase() {
        val dbPath = intent.getStringExtra(EXTRA_DB_PATH)
        val dbName = intent.getStringExtra(EXTRA_DB_NAME)

        if (dbPath.isNullOrEmpty()) {
            showToast("No database selected")
            finish()
            return
        }

        setupToolbar(dbName)
        initializeDatabaseAsync(dbPath)
    }

    private fun setupToolbar(dbName: String?) {
        // Set the database name in ViewModel for the top bar
        if (!dbName.isNullOrEmpty()) {
            viewModel.setDatabaseName(dbName)
        }
    }

    
        private fun initializeDatabaseAsync(dbPath: String) {
            launchCatching(
                dispatcher = Dispatchers.IO,
                block = {
                    Log.d(TAG, "Starting database initialization for: $dbPath")
                    viewModel.switchDatabase(dbPath)
                    try {
                        viewModel.state.first { it.subjectsResource !is Resource.Loading }
                        Log.d(TAG, "Database initialization completed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception waiting for subjects resource to load", e)
                        throw e
                    }
                },
                onSuccess = {
                    try {
                        mediaHandler = MediaHandler(this@QuizActivity)
                        filterDialogHandler = FilterDialogHandler(this@QuizActivity)
                        mediaHandler.reset()

                        Log.d(TAG, "Media and filter handlers initialized, setting up Compose UI")
                        Log.d(TAG, "About to call QuizRoot with filtersOnly=$filtersOnlyMode")

                        // Host the entire Quiz UI in Compose once DB is ready.
                        setContent {
                            MedicalQuizTheme {
                                com.medicalquiz.app.ui.QuizRoot(
                                viewModel = viewModel,
                                webViewStateFlow = webViewStateFlow,
                                mediaHandler = mediaHandler,
                                onSubjectFilter = { performSubjectSelection(false) },
                                onSystemFilter = { performSystemSelection(false) },
                                onClearFilters = { clearFilters() },
                                onSettings = { showSettingsDialog() },
                                onAbout = { showToast("About coming soon") },
                                onJumpTo = { showJumpToDialog() },
                                onStart = { startQuiz() }
                                )
                            }
                        }
                        Log.d(TAG, "Compose UI content set successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up Compose UI", e)
                        showErrorDialog("UI Setup Error", "Failed to initialize UI: ${e.message}")
                    }
                },
                onFailure = { throwable ->
                    Log.e(TAG, "Database initialization failed", throwable)
                    // Fatal initialization error - ask user to retry or close.
                    runOnUiThread {
                        AlertDialog.Builder(this@QuizActivity)
                            .setTitle("Database Error")
                            .setMessage("Failed to initialize database: ${throwable.message}")
                            .setPositiveButton("Close") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                }
            )
        }

    private fun setupUI() {
        setupBottomBarListeners()
    }

    // ============================================================================
    // WebView Setup
    // ============================================================================

    // Compose host is set after the database is initialized (see `initializeDatabaseAsync`)

    private fun createWebViewBridge() = object : WebViewController.Bridge {
        override fun onAnswerSelected(answerId: Long) {
            runOnUiThread {
                viewModel.onAnswerSelected(answerId)
                val timeTaken = java.lang.System.currentTimeMillis() - startTime
                viewModel.submitAnswer(timeTaken)
            }
        }

        override fun openMedia(mediaRef: String) {
            runOnUiThread {
                val ref = mediaRef.takeIf { it.isNotBlank() } ?: return@runOnUiThread
                val url = normalizeMediaUrl(ref)
                viewModel.openMedia(url)
            }
        }
    }

    private fun normalizeMediaUrl(ref: String): String {
        return if (ref.startsWith("file://") || 
                   ref.startsWith("http://") || 
                   ref.startsWith("https://") || 
                   ref.startsWith("media://")) {
            ref
        } else {
            "file:///media/${ref.substringAfterLast('/')}"
        }
    }

    // ============================================================================
    // Drawer Setup
    // ============================================================================

    private fun setupDrawer() {
        // Drawer is handled by Compose `QuizRoot`; no view setup necessary.
    }

    // ============================================================================
    // Listeners Setup
    // ============================================================================

    private fun setupListeners() {
        setupBottomBarListeners()
    }

    private fun setupBottomBarListeners() {
        // Bottom bar is handled by Compose `QuizRoot` — nothing to do here.
    }

    // ============================================================================
    // ViewModel State Observation
    // ============================================================================

    private fun observeViewModelState() {
        observeMainState()
        observeUiEvents()
    }

    private fun observeMainState() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousState: QuizState? = null
                
                viewModel.state.collect { state ->
                    updateNavigationControls(state)
                    handleQuestionListChanges(state, previousState)
                    handleQuestionChanges(state, previousState)
                    handlePerformanceChanges(state, previousState)
                    
                    previousState = state
                }
            }
        }
    }

    private fun handleQuestionListChanges(state: QuizState, previousState: QuizState?) {
        if (previousState?.questionIds == state.questionIds) return

        // When the activity first observes the state it may be empty because the
        // database hasn't been loaded yet. Don't show the "No questions" message
        // immediately on first observation — only show it when the question list
        // becomes empty after having been populated previously (or after a user action).
        if (state.questionIds.isEmpty()) {
            if (previousState != null) {
                showNoQuestionsFound()
            } else {
                // only show the start filters panel if the database has successfully
                // finished loading the subject list; if an error occurred we should
                // not show the filters panel erroneously.
                val dbReady = state.subjectsResource is Resource.Success
                if (dbReady) {
                    showStartFiltersPanel()
                }
            }
        } else {
            if (!filtersOnlyMode) {
                // Compose UI controls the start filters panel — nothing to do
            }
            if (viewModel.state.value.autoLoadFirstQuestion) {
                // Clear the flag in ViewModel before launching the load to avoid races
                viewModel.clearAutoLoadFirstQuestion()
                lifecycleScope.launch {
                    try {
                        // Wait for the ViewModel to populate questions (timeout to avoid hang)
                        kotlinx.coroutines.withTimeout(10_000) {
                            viewModel.state.first { it.questionIds.isNotEmpty() }
                        }
                        viewModel.loadQuestion(0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-load first question", e)
                    }
                }
            }
        }
    }

    private fun handleQuestionChanges(state: QuizState, previousState: QuizState?) {
        if (previousState?.currentQuestionId != state.currentQuestionId) {
            displayQuestion(state)
        }
    }

    private fun handlePerformanceChanges(state: QuizState, previousState: QuizState?) {
        if (previousState?.currentPerformance != state.currentPerformance) {
            updatePerformanceDisplay(state)
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is UiEvent.ShowToast -> showToast(event.message)
                        is UiEvent.OpenMedia -> mediaHandler.handleMediaLink(event.url)
                        is UiEvent.ShowAnswer -> handleShowAnswer(event)
                        else -> { /* Other events handled elsewhere */ }
                    }
                }
            }
        }
    }

    private fun handleShowAnswer(event: UiEvent.ShowAnswer) {
        updateWebViewAnswerState(event.correctAnswerId, event.selectedAnswerId)
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { viewModel.state.firstMatching() },
            onSuccess = { state -> showQuestionDetails(state) },
            onFailure = { showToast("Failed to show answer: ${it.message}") }
        )
    }

    // ============================================================================
    // Question Display
    // ============================================================================

    private fun displayQuestion(state: QuizState) {
        val question = state.currentQuestion ?: return
        startTime = java.lang.System.currentTimeMillis()

        launchCatching(
                dispatcher = Dispatchers.Default,
                block = {
                    val quizHtml = QuestionHtmlBuilder.build(question, state.currentAnswers)
                    val mediaFiles = HtmlUtils.collectMediaFiles(question)
                    quizHtml to mediaFiles
                },
                onSuccess = { (quizHtml, mediaFiles) ->
                    // Use Compose WebView composable via state flow for HTML content
                    webViewStateFlow.value = WebViewState(html = quizHtml)
                    updateQuestionMetadata(question, state)
                    updateMediaInfo(question.id, mediaFiles)
                    viewModel.resetAnswerState()
                    updateNavigationControls(state)
                    loadPerformanceForQuestion(question.id)
                },
                onFailure = { showToast("Failed to load question: ${it.message}") }
        )
    }

    private fun updateQuestionMetadata(question: Question, state: QuizState) {
        // Compose displays question metadata from the ViewModel state; no view update here.
        updatePerformanceDisplay(state)
    }

    private fun updatePerformanceDisplay(state: QuizState) {
        // Compose displays performance; update handled by ViewModel - no view update
    }

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int) {
        webViewStateFlow.value = webViewStateFlow.value.copy(correctAnswerId = correctAnswerId, selectedAnswerId = selectedAnswerId)
    }

    // ============================================================================
    // Navigation Controls
    // ============================================================================

    private fun updateNavigationControls(state: QuizState) {
        // Compose bottom bar handles navigation and counters directly from ViewModel.
    }

    private fun showJumpToDialog() {
        lifecycleScope.launch {
            val state = viewModel.state.firstMatching()
            if (state.questionIds.isEmpty()) return@launch

            val picker = android.widget.NumberPicker(this@QuizActivity).apply {
                minValue = 1
                maxValue = state.questionIds.size
                value = state.currentQuestionIndex + 1
                wrapSelectorWheel = false
            }

            AlertDialog.Builder(this@QuizActivity)
                .setTitle("Jump to question")
                .setView(picker)
                .setPositiveButton("Go") { _, _ ->
                    viewModel.loadQuestion(picker.value - 1)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    // ============================================================================
    // Start Filters Panel
    // ============================================================================

    private fun showStartFiltersPanel() {
        // Use bound included layout access rather than manual findViewById
            // Compose `StartFiltersPanel` shown by `QuizRoot` — nothing to set manually here.
        // Hide cancel button for filters-only mode
        if (filtersOnlyMode) {
            // Hide the cancel button by recomposing the filters panel; it will be
            // omitted in `StartFiltersPanel` in filters-only mode if necessary.
        }
        setupFilterPanelListeners()
        updateAllFilterLabels()
    }

    private fun setupFilterPanelListeners() {
        // Interactions with the filters panel are handled via the Compose
        // StartFiltersPanel callbacks which are set in `showStartFiltersPanel()`.
    }

    private fun performSubjectSelection(silently: Boolean, onAfterApply: (() -> Unit)? = null) {
        Log.d(TAG, "performSubjectSelection called, silently=$silently")
        viewModel.fetchSubjects()

        lifecycleScope.launch {
            // Wait until the subjects resource is no longer loading (single-shot)
            try {
                val state = kotlinx.coroutines.withTimeout(10_000) { viewModel.state.first { it.subjectsResource !is Resource.Loading } }
                val resource = state.subjectsResource
                when (resource) {
                    is Resource.Success<List<Subject>> -> {
                        Log.d(TAG, "Subjects loaded: ${resource.data.size} subjects")
                        if (silently) {
                            filterDialogHandler.showSubjectSelectionDialogSilently(
                                resource.data,
                                state.selectedSubjectIds,
                                viewModel
                            ) {
                                onAfterApply?.invoke()
                            }
                        } else {
                            filterDialogHandler.showSubjectSelectionDialog(
                                resource.data,
                                state.selectedSubjectIds,
                                viewModel
                            ) {
                                onAfterApply?.invoke()
                            }
                        }
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Error fetching subjects: ${resource.message}")
                        showToast("Error fetching subjects: ${resource.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Timeout/wait error waiting for subjects to load", e)
                showToast("Failed to load subjects: ${e.message}")
            }
        }
    }

    private fun performSystemSelection(silently: Boolean, onAfterApply: (() -> Unit)? = null) {
        Log.d(TAG, "performSystemSelection called, silently=$silently")
        // Use a snapshot of the subjects selection now, and pass the same
        // snapshot to the systems fetch call to avoid race when the user
        // changes their subject selection rapidly.
        val subjectsSnapshot = viewModel.state.value.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
        viewModel.fetchSystemsForSubjects(subjectsSnapshot)

        lifecycleScope.launch {
            // Wait until the systems resource is no longer loading (single-shot)
            try {
                val state = kotlinx.coroutines.withTimeout(10_000) { viewModel.state.first { it.systemsResource !is Resource.Loading } }
                val resource = state.systemsResource
                when (resource) {
                    is Resource.Success<List<System>> -> {
                        Log.d(TAG, "Systems loaded: ${resource.data.size} systems")
                        if (silently) {
                            filterDialogHandler.showSystemSelectionDialogSilently(
                                resource.data,
                                state.selectedSystemIds,
                                viewModel
                            ) {
                                onAfterApply?.invoke()
                            }
                        } else {
                            filterDialogHandler.showSystemSelectionDialog(
                                resource.data,
                                state.selectedSystemIds,
                                viewModel
                            ) {
                                onAfterApply?.invoke()
                            }
                        }
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Error fetching systems: ${resource.message}")
                        showToast("Error fetching systems: ${resource.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Timeout/wait error waiting for systems to load", e)
                showToast("Failed to load systems: ${e.message}")
            }
        }
    }

    private fun handleSubjectSelection() {
        performSubjectSelection(true) {
            viewModel.fetchSystemsForSubjects(viewModel.state.value.selectedSubjectIds.toList())
            updateAllFilterLabels()
        }
    }

    private fun handleSystemSelection() {
        performSystemSelection(true) { updateAllFilterLabels() }
    }

    private fun handlePerformanceSelection() {
        // Performance filter is now a Compose dialog in `QuizRoot`.
        // This method remains for compatibility.
    }

    private fun hideFilterPanel() {
            // Compose `StartFiltersPanel` displayed in `QuizRoot`.
        updateToolbarSubtitle()
    }

    private fun startQuiz() {
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = {
                val ids = viewModel.fetchFilteredQuestionIds()
                viewModel.setQuestionIds(ids)
                ids
            },
            onSuccess = { ids ->
                // Hide filter panel first to ensure WebView is visible
                // Compose `StartFiltersPanel` displayed in `QuizRoot`.
                // Compose handles filter panel visibility; nothing to do
                
                if (ids.isNotEmpty()) {
                    // Wait until the ViewModel state reflects the new question ids
                    lifecycleScope.launch {
                        try {
                            // Use structural equality (containsAll + size) so new lists
                            // with the same content cause completion.
                            kotlinx.coroutines.withTimeout(10_000) {
                                viewModel.state.first {
                                    it.questionIds.size == ids.size && it.questionIds.containsAll(ids)
                                }
                            }
                            viewModel.loadQuestion(0)
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.e(TAG, "Timeout waiting for question ids to update", e)
                            // Verify state has at least some questions before attempting load
                            val currentIds = viewModel.state.value.questionIds
                            if (currentIds.isNotEmpty() && currentIds.size == ids.size) {
                                Log.w(TAG, "Attempting to load anyway after timeout")
                                viewModel.loadQuestion(0)
                            } else {
                                showToast("Failed to load questions - please try again")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error waiting for question ids", e)
                            showToast("Error: ${e.message}")
                        }
                    }
                } else {
                    showToast("No questions found for current filters")
                }
            },
            onFailure = { showToast("Failed to start: ${it.message}") }
        )
    }

    private fun updateAllFilterLabels() {
        // Compose `StartFiltersPanel` is driven by `viewModel` state; no manual setContent.
        // Recompute preview separately (async)
        updatePreviewCount()
    }

    private fun updateSubjectLabel() {
        // Labels are handled by Compose; recompose the filters panel
        updateAllFilterLabels()
        // If no subjects are selected it used to mean "systems not applicable" —
        // but we treat an empty subject selection as "All subjects" now. Systems
        // are still available in that case, so keep the systems button enabled.
        // `StartFiltersPanel` Compose component tracks the enabled state through `viewModel`.
    }

    private fun updateSystemLabel() {
        updateAllFilterLabels()
    }

    private fun updatePerformanceLabel() {
        updateAllFilterLabels()
    }

    private fun updatePreviewCount() {
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { viewModel.fetchFilteredQuestionIds().size },
            onSuccess = { count ->
                Log.d(TAG, "Preview count: $count")
                // Update the preview count in the ViewModel state
                viewModel.updatePreviewQuestionCount()
            },
            onFailure = {
                Log.e(TAG, "Failed to update preview count: ${it.message}")
            }
        )
    }

    // ============================================================================
    // Navigation Drawer Handling
    // ============================================================================

    // Navigation is handled in `QuizRoot` via Compose drawer.

    private fun showSubjectFilterDialog() {
        performSubjectSelection(filtersOnlyMode)
    }

    private fun showSystemFilterDialog() {
        performSystemSelection(filtersOnlyMode)
    }

    private fun showPerformanceFilterDialog() {
        val filters = PerformanceFilter.values()
        val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
        
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { viewModel.state.firstMatching() },
            onSuccess = { state ->
                val currentIndex = filters.indexOf(state.performanceFilter)

                AlertDialog.Builder(this@QuizActivity)
                    .setTitle("Performance Filter")
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        dialog.dismiss()
                        if (state.performanceFilter != filters[which]) {
                            applyPerformanceFilter(filters[which])
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
            onFailure = { showToast("Failed to get performance filter: ${it.message}") }
        )
    }

    private fun applyPerformanceFilter(filter: PerformanceFilter) {
        viewModel.setPerformanceFilter(filter)
        settingsRepository.setPerformanceFilter(filter)
        reloadQuestionsWithFilters()
    }

    private fun clearFilters() {
        viewModel.applySelectedSubjects(emptySet())
        viewModel.setSelectedSystems(emptySet())
        viewModel.setPerformanceFilter(PerformanceFilter.ALL)
        settingsRepository.setPerformanceFilter(PerformanceFilter.ALL)
        // Don't load questions - just clear the filters
        // The filter panel will update automatically via Compose state
        updatePreviewCount()
    }

    private fun reloadQuestionsWithFilters() {
        viewModel.loadFilteredQuestionIds()
    }

    // ============================================================================
    // Settings Dialog
    // ============================================================================

    private fun showSettingsDialog() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        composeView.setContent {
            MedicalQuizTheme {
                val loggingEnabled by settingsRepository.isLoggingEnabled.collectAsStateWithLifecycle()
                com.medicalquiz.app.ui.SettingsDialog(
                    initialLoggingEnabled = loggingEnabled,
                    onLoggingChanged = { enabled ->
                        settingsRepository.setLoggingEnabled(enabled)
                        if (!enabled) viewModel.clearPendingLogsBuffer()
                    },
                    onResetLogs = { showResetLogsConfirmation() }
                )
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(composeView)
            .setPositiveButton("Close", null)
            .create()

        dialog.show()
    }

    private fun showResetLogsConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset log history")
            .setMessage("This will permanently delete all stored answer logs. Continue?")
            .setPositiveButton("Delete") { _, _ ->
                launchCatching(
                    dispatcher = Dispatchers.IO,
                    block = { viewModel.clearLogsFromDb() },
                    onSuccess = { showToast("Logs cleared") },
                    onFailure = { showToast("Failed to clear logs: ${it.message}") }
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ============================================================================
    // State Persistence
    // ============================================================================

    private fun restoreStateIfNeeded(savedInstanceState: Bundle?) {
        savedInstanceState?.let { restoreInstanceState(it) }
    }

    private fun restoreInstanceState(savedInstanceState: Bundle) {
        val dbPath = intent.getStringExtra(EXTRA_DB_PATH) ?: return
        
        launchCatching(
            block = {
                if (::mediaHandler.isInitialized) {
                    mediaHandler.reset()
                }
                restoreQuizState(savedInstanceState)
                
                val questionId = savedInstanceState.getLong(STATE_CURRENT_QUESTION_ID, -1)
                if (questionId != -1L) {
                    viewModel.restoreQuestionFromDatabase(questionId)
                }
            },
            onSuccess = {
                handleRestoreSuccess()
            },
            onFailure = { throwable ->
                handleRestoreFailure(throwable, dbPath)
            }
        )
    }

    private fun handleRestoreSuccess() {
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { viewModel.state.firstMatching() },
            onSuccess = { state ->
                if (state.questionIds.isEmpty()) {
                    // `loadQuestionsAfterRestore()` is a suspend function; run
                    // it in a coroutine
                    lifecycleScope.launch { loadQuestionsAfterRestore() }
                } else {
                    restoreCurrentQuestion(state)
                }
            },
            onFailure = { throwable -> Log.e(TAG, "Failed to get state during restore", throwable) }
        )
    }

    private suspend fun loadQuestionsAfterRestore() {
        try {
            val ids = viewModel.fetchFilteredQuestionIds()
            val state = viewModel.state.firstMatching()
            
            if (state.questionIds.isNotEmpty()) {
                viewModel.loadQuestion(0)
            } else {
                showNoQuestionsFound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch questions during restore", e)
        }
    }

    private fun restoreCurrentQuestion(state: QuizState) {
        if (state.currentQuestion != null) {
            displayQuestion(state)
            loadPerformanceForQuestion(state.currentQuestion!!.id)
        } else if (state.questionIds.isNotEmpty()) {
            viewModel.loadQuestion(state.currentQuestionIndex)
        }
        
        updateToolbarSubtitle()
    }

    private fun handleRestoreFailure(throwable: Throwable, dbPath: String) {
        showToast("Error restoring state: ${throwable.message}")
        Log.e(TAG, "Failed to restore instance state", throwable)
        initializeDatabase(dbPath)
    }

    private fun restoreQuizState(savedInstanceState: Bundle) {
        val currentIndex = savedInstanceState.getInt(STATE_CURRENT_QUESTION_INDEX, 0)
        val ids = savedInstanceState.getLongArray(STATE_QUESTION_IDS)?.toList() ?: emptyList()
        val subjects = savedInstanceState.getLongArray(STATE_SELECTED_SUBJECT_IDS)?.toList() ?: emptyList()
        val systems = savedInstanceState.getLongArray(STATE_SELECTED_SYSTEM_IDS)?.toList() ?: emptyList()
        
        viewModel.setSelectedSubjects(subjects.toSet())
        viewModel.setSelectedSystems(systems.toSet())
        
        val performanceFilter = savedInstanceState.getString(STATE_PERFORMANCE_FILTER)
            ?.let { PerformanceFilter.valueOf(it) } ?: PerformanceFilter.ALL
        viewModel.setPerformanceFilter(performanceFilter)

        viewModel.setQuestionIds(ids)
        if (ids.isNotEmpty()) {
            val safeIndex = currentIndex.coerceIn(0, ids.size - 1)
            viewModel.loadQuestion(safeIndex)
        }

        val wasSubmitted = savedInstanceState.getBoolean(STATE_ANSWER_SUBMITTED, false)
        val selectedAnswer = if (savedInstanceState.containsKey(STATE_SELECTED_ANSWER_ID)) {
            savedInstanceState.getInt(STATE_SELECTED_ANSWER_ID)
        } else null
        viewModel.setAnswerSubmissionState(wasSubmitted, selectedAnswer)

        savedInstanceState.getString(STATE_TEST_ID)?.let { viewModel.setTestId(it) }
    }

    private fun saveQuizState(outState: Bundle) {
        val state = viewModel.state.value
        
        with(outState) {
            putInt(STATE_CURRENT_QUESTION_INDEX, state.currentQuestionIndex)
            putLongArray(STATE_QUESTION_IDS, state.questionIds.toLongArray())
            putLongArray(STATE_SELECTED_SUBJECT_IDS, state.selectedSubjectIds.toLongArray())
            putLongArray(STATE_SELECTED_SYSTEM_IDS, state.selectedSystemIds.toLongArray())
            putString(STATE_PERFORMANCE_FILTER, state.performanceFilter.name)
            putString(STATE_TEST_ID, viewModel.getTestId())
            putLong(STATE_START_TIME, startTime)
            putBoolean(STATE_ANSWER_SUBMITTED, state.answerSubmitted)
            state.selectedAnswerId?.let { putInt(STATE_SELECTED_ANSWER_ID, it) }
            putLong(STATE_CURRENT_QUESTION_ID, state.currentQuestion?.id ?: -1L)
        }
    }

    // ============================================================================
    // UI Updates
    // ============================================================================

    private fun showNoQuestionsFound() {
        showToast("No questions found with selected filters")
        updateToolbarSubtitle()
    }

    private fun showQuestionDetails(state: QuizState) {
        // Compose handles showing question details; scroll behavior is handled
        // inside `QuizScreen`/`WebViewComposable` and is not needed here.
    }

    private fun updateToolbarSubtitle() {
        // Toolbar subtitle is managed by the Compose `QuizRoot` via ViewModel.
        // No-op — kept for compatibility until all XML references are removed.
    }

    private fun applyWindowInsets() {
        // Window insets are delivered to Compose when running with Compose. No
        // explicit view-based insets required here.
    }

    // ============================================================================
    // Media Handling
    // ============================================================================

    private fun updateMediaInfo(questionId: Long, mediaFiles: List<String>) {
        cacheManager.updateMediaCache(questionId, mediaFiles)
        // Delegate to MediaHandler — it will maintain the current media list for the question.
        mediaHandler.updateMedia(questionId, mediaFiles)

        Log.d(TAG, "Question $questionId has media files: $mediaFiles")
    }

    // ============================================================================
    // Performance Tracking
    // ============================================================================

    private fun loadPerformanceForQuestion(questionId: Long) {
        viewModel.loadPerformanceForQuestion(questionId)
    }

    // ============================================================================
    // Helper Functions
    // ============================================================================

    private fun getPerformanceFilterLabel(filter: PerformanceFilter): String = when (filter) {
        PerformanceFilter.ALL -> "All Questions"
        PerformanceFilter.UNANSWERED -> "Not Attempted"
        PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
        PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
        PerformanceFilter.EVER_CORRECT -> "Ever Correct"
        PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
    }

    private fun clearCaches() {
        cacheManager.clearCaches()
        HtmlUtils.clearMediaCaches()
    }

    private fun initializeDatabase(dbPath: String) {
        clearCaches()
        viewModel.initializeDatabase(dbPath)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    // ============================================================================
    // Back Press Handling
    // ============================================================================

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed, questionsLoaded=${viewModel.state.value.questionIds.isNotEmpty()}")
                
                // Only exit if questions are loaded (we're in quiz mode)
                // If in filter selection phase, back does nothing
                if (viewModel.state.value.questionIds.isNotEmpty()) {
                    finish()
                }
                // Otherwise in filter panel - back does nothing, must use filters to proceed
            }
        })
    }

    // ============================================================================
    // Constants
    // ============================================================================

    companion object {
        private const val TAG = "QuizActivity"
        
        // Intent extras
        const val EXTRA_DB_PATH = "DB_PATH"
        const val EXTRA_DB_NAME = "DB_NAME"
        
        // Instance state keys
        private const val STATE_CURRENT_QUESTION_INDEX = "current_question_index"
        private const val STATE_QUESTION_IDS = "question_ids"
        private const val STATE_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        private const val STATE_SELECTED_SYSTEM_IDS = "selected_system_ids"
        private const val STATE_PERFORMANCE_FILTER = "performance_filter"
        private const val STATE_TEST_ID = "test_id"
        private const val STATE_START_TIME = "start_time"
        private const val STATE_ANSWER_SUBMITTED = "answer_submitted"
        private const val STATE_SELECTED_ANSWER_ID = "selected_answer_id"
        private const val STATE_CURRENT_QUESTION_ID = "current_question_id"
    }

    // Flag set when activity should present only the full-screen filters UI
    private var filtersOnlyMode: Boolean = false
}