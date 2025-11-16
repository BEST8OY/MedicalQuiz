package com.medicalquiz.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import android.view.View
import androidx.lifecycle.Lifecycle
import android.view.ViewGroup
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
import android.view.MenuItem
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.databinding.DialogSettingsBinding
import com.medicalquiz.app.ui.FilterDialogHandler
import com.medicalquiz.app.ui.MediaHandler
import com.medicalquiz.app.ui.QuizState
import com.medicalquiz.app.utils.HtmlUtils
import com.medicalquiz.app.utils.QuestionHtmlBuilder
import com.medicalquiz.app.utils.Resource
import com.medicalquiz.app.utils.WebViewController
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.firstMatching
import com.medicalquiz.app.utils.launchCatching
import com.medicalquiz.app.viewmodel.QuizViewModel
import com.medicalquiz.app.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    // View Binding
    private lateinit var binding: ActivityQuizBinding
    
    // ViewModel
    private val viewModel: QuizViewModel by viewModels()
    
    // UI Handlers
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mediaHandler: MediaHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    private lateinit var webViewController: WebViewController
    
    // Repositories
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cacheManager: CacheManager
    
    // State
    private var startTime: Long = 0
    private var autoLoadFirstQuestion: Boolean = false

    // ============================================================================
    // Lifecycle Methods
    // ============================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        validateAndSetupDatabase()
        setupUI()
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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        handleNavigationItemSelected(item.itemId)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // ============================================================================
    // Initialization
    // ============================================================================

    private fun initializeComponents() {
        binding.scrollViewContent.clipToPadding = false
        applyWindowInsets()

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
        filtersOnlyMode = intent.getBooleanExtra(EXTRA_OPEN_FILTERS_FULLSCREEN, false)

        if (dbPath.isNullOrEmpty()) {
            showToast("No database selected")
            finish()
            return
        }

        setupToolbar(dbName)
        if (filtersOnlyMode) {
            hideUiForFiltersOnlyMode()
        }
        initializeDatabaseAsync(dbPath)
    }

    private fun setupToolbar(dbName: String?) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = dbName
            setDisplayHomeAsUpEnabled(true)
        }
    }

    
        private fun initializeDatabaseAsync(dbPath: String) {
            launchCatching(
                dispatcher = Dispatchers.IO,
                block = {
                    viewModel.switchDatabase(dbPath)
                    try {
                        viewModel.state.first { it.subjectsResource !is Resource.Loading }
                    } catch (_: Exception) { /* ignore */ }
                },
                onSuccess = {
                    mediaHandler = MediaHandler(this@QuizActivity)
                    filterDialogHandler = FilterDialogHandler(this@QuizActivity)
                    mediaHandler.reset()

                    setupWebViews()
                    setupDrawer()
                    setupListeners()
                    showStartFiltersPanel()
                },
                onFailure = { throwable ->
                    showToast("Failed to initialize database: ${throwable.message}")
                }
            )
        }

    private fun setupUI() {
        setupBottomBarListeners()
    }

    // ============================================================================
    // WebView Setup
    // ============================================================================

    private fun setupWebViews() {
        webViewController = WebViewController(mediaHandler)
        webViewController.setup(binding.webViewQuestion, createWebViewBridge())
    }

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
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    // ============================================================================
    // Listeners Setup
    // ============================================================================

    private fun setupListeners() {
        setupBottomBarListeners()
    }

    private fun setupBottomBarListeners() {
        binding.buttonPrevious.setOnClickListener { viewModel.loadPrevious() }
        binding.buttonNext.setOnClickListener { viewModel.loadNext() }
        binding.counterContainer.apply {
            setOnClickListener { showJumpToDialog() }
            contentDescription = "Tap to jump to question"
        }
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
                // initial empty state — open the start filters panel instead
                showStartFiltersPanel()
            }
        } else {
            showQuestionsLoaded(state.questionIds.size)
            if (autoLoadFirstQuestion) {
                viewModel.loadQuestion(0)
                autoLoadFirstQuestion = false
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
                    WebViewRenderer.loadContent(this@QuizActivity, binding.webViewQuestion, quizHtml)
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
        val metadata = buildString {
            append("ID: ${question.id}")
            if (!question.subName.isNullOrBlank()) append(" | Subject: ${question.subName}")
            if (!question.sysName.isNullOrBlank()) append(" | System: ${question.sysName}")
        }

        binding.textViewMetadata.apply {
            text = metadata
            isVisible = false
        }

        updatePerformanceDisplay(state)
    }

    private fun updatePerformanceDisplay(state: QuizState) {
        binding.textViewPerformance.apply {
            text = buildPerformanceSummary(state.currentPerformance)
            isVisible = !text.isNullOrBlank() && state.isLoggingEnabled
        }
    }

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int) {
        webViewController.applyAnswerState(binding.webViewQuestion, correctAnswerId, selectedAnswerId)
    }

    // ============================================================================
    // Navigation Controls
    // ============================================================================

    private fun updateNavigationControls(state: QuizState) {
        val total = state.questionIds.size
        binding.buttonNext.isEnabled = state.currentQuestionIndex < total - 1
        binding.buttonPrevious.isEnabled = state.currentQuestionIndex > 0
        binding.textViewQuestionIndex.text = (state.currentQuestionIndex + 1).toString()
        binding.textViewTotalQuestions.text = "/ $total"
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
        binding.startFiltersPanel.root.visibility = View.VISIBLE
        // Hide cancel button for filters-only mode
        if (filtersOnlyMode) {
            binding.startFiltersPanel.buttonCancelPanel.visibility = View.GONE
        }
        setupFilterPanelListeners()
        updateAllFilterLabels()
    }

    private fun hideUiForFiltersOnlyMode() {
        // Hide the main UI elements so only the filter panel is visible full-screen
        binding.toolbar.visibility = View.GONE
        binding.cardQuestion.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.navigationView.visibility = View.GONE

        // Expand the start filters panel to full-screen height
        val params = binding.startFiltersPanel.root.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        binding.startFiltersPanel.root.layoutParams = params
    }

    private fun setupFilterPanelListeners() {
        // Listeners on start filters panel
        {
            // Use activity root to resolve view IDs reliably
            binding.startFiltersPanel.buttonSelectSubjectsPanel
                .setOnClickListener { handleSubjectSelection() }
            
            binding.startFiltersPanel.buttonSelectSystemsPanel
                .setOnClickListener { handleSystemSelection() }
            
            binding.startFiltersPanel.buttonSelectPerformancePanel
                .setOnClickListener { handlePerformanceSelection() }
            
            binding.startFiltersPanel.buttonCancelPanel
                .setOnClickListener { hideFilterPanel() }
            
            binding.startFiltersPanel.buttonStartPanel
                .setOnClickListener { startQuiz() }
        }
    }

    private fun handleSubjectSelection() {
        viewModel.fetchSubjects()
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = {
                viewModel.state
                    .first { it.subjectsResource !is Resource.Loading }
                    .subjectsResource
            },
            onSuccess = { resource ->
                when (resource) {
                    is Resource.Success<List<Subject>> -> {
                        // `firstMatching()` is a suspend function; call it from a coroutine
                        lifecycleScope.launch {
                            val state = viewModel.state.firstMatching()
                            filterDialogHandler.showSubjectSelectionDialogSilently(
                                resource.data,
                                state.selectedSubjectIds,
                                viewModel
                            ) {
                                viewModel.fetchSystemsForSubjects(viewModel.state.value.selectedSubjectIds.toList())
                                updateAllFilterLabels()
                            }
                        }
                    }
                    is Resource.Error -> showToast("Error fetching subjects: ${resource.message}")
                    else -> { /* ignore */ }
                }
            },
            onFailure = { showToast("Failed to fetch subjects: ${it.message}") }
        )
    }

    private fun handleSystemSelection() {
        val state = viewModel.state.value
        viewModel.fetchSystemsForSubjects(state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList())
        
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = {
                viewModel.state
                    .first { it.systemsResource !is Resource.Loading }
                    .systemsResource
            },
            onSuccess = { resource ->
                when (resource) {
                    is Resource.Success<List<System>> -> {
                        filterDialogHandler.showSystemSelectionDialogSilently(
                            resource.data,
                            viewModel.state.value.selectedSystemIds,
                            viewModel
                        ) {
                            updateAllFilterLabels()
                        }
                    }
                    is Resource.Error -> showToast("Error fetching systems: ${resource.message}")
                    else -> { /* ignore */ }
                }
            },
            onFailure = { showToast("Failed to fetch systems: ${it.message}") }
        )
    }

    private fun handlePerformanceSelection() {
        val filters = PerformanceFilter.values()
        val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
        val currentIndex = filters.indexOf(viewModel.state.value.performanceFilter)

        AlertDialog.Builder(this)
            .setTitle("Performance Filter")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                dialog.dismiss()
                viewModel.setPerformanceFilter(filters[which])
                updateAllFilterLabels()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun hideFilterPanel() {
        binding.startFiltersPanel.root.visibility = View.GONE
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
                if (ids.isNotEmpty()) {
                    viewModel.loadQuestion(0)
                } else {
                    binding.textViewStatus.text = "No questions found for current filters"
                }
            },
            onFailure = { showToast("Failed to start: ${it.message}") }
        )
        // Always hide filter panel and restore UI if needed
        binding.startFiltersPanel.root.visibility = View.GONE
        if (filtersOnlyMode) {
            binding.toolbar.visibility = View.VISIBLE
            binding.cardQuestion.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
            binding.navigationView.visibility = View.VISIBLE
            filtersOnlyMode = false
        }
    }

    private fun updateAllFilterLabels() {
        updateSubjectLabel()
        updateSystemLabel()
        updatePerformanceLabel()
        updatePreviewCount()
    }

    private fun updateSubjectLabel() {
        val count = viewModel.state.value.selectedSubjectIds.size
        binding.startFiltersPanel
            .buttonSelectSubjectsPanel
            .text = if (count == 0) "Subjects: All" else "Subjects: $count"
        // If no subjects are selected it used to mean "systems not applicable" —
        // but we treat an empty subject selection as "All subjects" now. Systems
        // are still available in that case, so keep the systems button enabled.
        binding.startFiltersPanel
            .buttonSelectSystemsPanel
            .isEnabled = true
    }

    private fun updateSystemLabel() {
        val count = viewModel.state.value.selectedSystemIds.size
        binding.startFiltersPanel
            .buttonSelectSystemsPanel
            .text = if (count == 0) "Systems: All" else "Systems: $count"
    }

    private fun updatePerformanceLabel() {
        val filter = viewModel.state.value.performanceFilter
        binding.startFiltersPanel
            .buttonSelectPerformancePanel
            .text = getPerformanceFilterLabel(filter)
    }

    private fun updatePreviewCount() {
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { viewModel.fetchFilteredQuestionIds().size },
            onSuccess = { count ->
                val text = if (count == 1) "1 question matches" else "$count questions match"
                binding.startFiltersPanel.textViewPreviewCountPanel.text = text
                binding.startFiltersPanel.buttonStartPanel.isEnabled = count > 0
            },
            onFailure = {
                binding.startFiltersPanel.textViewPreviewCountPanel.text = "Preview unavailable"
                binding.startFiltersPanel.buttonStartPanel.isEnabled = true
            }
        )
    }

    // ============================================================================
    // Navigation Drawer Handling
    // ============================================================================

    private fun handleNavigationItemSelected(itemId: Int) {
        when (itemId) {
            R.id.nav_filter_subject -> showSubjectFilterDialog()
            R.id.nav_filter_system -> showSystemFilterDialog()
            R.id.nav_filter_performance -> showPerformanceFilterDialog()
            R.id.nav_clear_filters -> clearFilters()
            R.id.nav_settings -> showSettingsDialog()
            R.id.nav_about -> showToast("About coming soon")
        }
    }

    private fun showSubjectFilterDialog() {
        viewModel.fetchSubjects()
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = {
                viewModel.state
                    .first { it.subjectsResource !is Resource.Loading }
                    .subjectsResource
            },
            onSuccess = { resource ->
                when (resource) {
                    is Resource.Success<List<Subject>> -> {
                        // `firstMatching()` is suspend; launch a coroutine on the main
                        // thread to get state and show the dialog
                        lifecycleScope.launch {
                            val state = viewModel.state.firstMatching()
                            filterDialogHandler.showSubjectSelectionDialog(
                                resource.data,
                                state.selectedSubjectIds,
                                viewModel
                            )
                        }
                    }
                    is Resource.Error -> showToast("Error fetching subjects: ${resource.message}")
                    else -> { /* ignore */ }
                }
            },
            onFailure = { showToast("Failed to fetch subjects: ${it.message}") }
        )
    }

    private fun showSystemFilterDialog() {
        val state = viewModel.state.value
        viewModel.fetchSystemsForSubjects(state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList())
        
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = {
                viewModel.state
                    .first { it.systemsResource !is Resource.Loading }
                    .systemsResource
            },
            onSuccess = { resource ->
                when (resource) {
                    is Resource.Success<List<System>> -> {
                        // `firstMatching()` is suspend; do it inside a coroutine
                        lifecycleScope.launch {
                            val currentState = viewModel.state.firstMatching()
                            filterDialogHandler.showSystemSelectionDialog(
                                resource.data,
                                currentState.selectedSystemIds,
                                viewModel
                            )
                        }
                    }
                    is Resource.Error -> showToast("Error fetching systems: ${resource.message}")
                    else -> { /* ignore */ }
                }
            },
            onFailure = { showToast("Failed to fetch systems: ${it.message}") }
        )
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
        reloadQuestionsWithFilters()
    }

    private fun reloadQuestionsWithFilters() {
        viewModel.loadFilteredQuestionIds()
    }

    // ============================================================================
    // Settings Dialog
    // ============================================================================

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.switchLogAnswers.isChecked = settingsRepository.isLoggingEnabled.value

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.switchLogAnswers.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setLoggingEnabled(isChecked)
            if (!isChecked) {
                viewModel.clearPendingLogsBuffer()
            }
        }

        dialogBinding.buttonResetLogs.setOnClickListener {
            showResetLogsConfirmation()
        }

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
        
        binding.textViewStatus.text = "Restoring quiz state..."
        
        launchCatching(
            block = {
                mediaHandler.reset()
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
                showQuestionsLoaded(state.questionIds.size)
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
        showQuestionsLoaded(state.questionIds.size)
    }

    private fun handleRestoreFailure(throwable: Throwable, dbPath: String) {
        binding.textViewStatus.text = "Error restoring state: ${throwable.message}"
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
        if (currentIndex in ids.indices) {
            viewModel.loadQuestion(currentIndex)
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
        binding.textViewStatus.apply {
            text = "No questions found for current filters"
            isVisible = true
        }
        updateToolbarSubtitle()
    }

    private fun showQuestionsLoaded(count: Int) {
        binding.textViewStatus.apply {
            text = "Loaded $count questions"
            isVisible = true
        }
        updateToolbarSubtitle()
    }

    private fun showQuestionDetails(state: QuizState) {
        val previousScrollY = binding.scrollViewContent.scrollY

        binding.textViewMetadata.isVisible = !binding.textViewMetadata.text.isNullOrBlank()
        binding.textViewPerformance.isVisible = 
            !binding.textViewPerformance.text.isNullOrBlank() && state.isLoggingEnabled
        binding.textViewMediaInfo.isVisible = !binding.textViewMediaInfo.text.isNullOrBlank()

        binding.scrollViewContent.post {
            binding.scrollViewContent.scrollTo(0, previousScrollY)
        }
    }

    private fun updateToolbarSubtitle() {
        supportActionBar?.subtitle = null
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollViewContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraBottom = resources.getDimensionPixelSize(R.dimen.quiz_scroll_bottom_padding)
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                extraBottom + systemBars.bottom
            )
            insets
        }
    }

    // ============================================================================
    // Media Handling
    // ============================================================================

    private fun updateMediaInfo(questionId: Long, mediaFiles: List<String>) {
        cacheManager.updateMediaCache(questionId, mediaFiles)
        mediaHandler.updateMedia(questionId, mediaFiles)
        
        binding.textViewMediaInfo.apply {
            if (mediaFiles.isNotEmpty()) {
                text = "📎 ${mediaFiles.size} media file(s) - Tap to view"
                setOnClickListener { mediaHandler.showCurrentMediaGallery() }
            } else {
                text = ""
                setOnClickListener(null)
            }
            isVisible = false
        }
        
        Log.d(TAG, "Question $questionId has media files: $mediaFiles")
    }

    // ============================================================================
    // Performance Tracking
    // ============================================================================

    private fun loadPerformanceForQuestion(questionId: Long) {
        viewModel.loadPerformanceForQuestion(questionId)
    }

    private fun buildPerformanceSummary(performance: QuestionPerformance?): String {
        val summary = performance?.takeIf { it.attempts > 0 } ?: return ""
        val lastLabel = if (summary.lastCorrect) "Correct" else "Incorrect"
        return "Attempts: ${summary.attempts} | Last: $lastLabel"
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
        binding.textViewStatus.text = "Loading database..."
        clearCaches()
        viewModel.initializeDatabase(dbPath)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ============================================================================
    // Back Press Handling
    // ============================================================================

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
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
        const val EXTRA_OPEN_FILTERS_FULLSCREEN = "OPEN_FILTERS_FULLSCREEN"
        
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