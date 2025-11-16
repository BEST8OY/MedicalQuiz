package com.medicalquiz.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.widget.Toast
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.activity.viewModels
import com.google.android.material.navigation.NavigationView
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.databinding.DialogSettingsBinding
import com.medicalquiz.app.ui.FilterDialogHandler
import com.medicalquiz.app.ui.MediaHandler
import kotlinx.coroutines.flow.first
import com.medicalquiz.app.utils.firstMatching
import androidx.lifecycle.repeatOnLifecycle
import com.medicalquiz.app.utils.Resource
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
// ViewModel handles DB switching/state

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityQuizBinding
    private val viewModel: com.medicalquiz.app.viewmodel.QuizViewModel by viewModels()
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mediaHandler: MediaHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    private lateinit var webViewController: com.medicalquiz.app.utils.WebViewController
    private lateinit var settingsRepository: com.medicalquiz.app.data.SettingsRepository
    private lateinit var cacheManager: com.medicalquiz.app.data.CacheManager

    // UI state is driven by ViewModel.state — avoid duplicating it in Activity
    private var startTime: Long = 0
    private var autoLoadFirstQuestion: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollViewContent.clipToPadding = false
        applyWindowInsets()

        settingsRepository = com.medicalquiz.app.data.SettingsRepository(this)
        cacheManager = com.medicalquiz.app.data.CacheManager()
        viewModel.setSettingsRepository(settingsRepository)
        viewModel.setCacheManager(cacheManager)

        val initialPerformance = settingsRepository.performanceFilter.value
        viewModel.setPerformanceFilter(initialPerformance)
        settingsRepository.setPerformanceFilter(initialPerformance)

        val dbPath = intent.getStringExtra(EXTRA_DB_PATH)
        val dbName = intent.getStringExtra(EXTRA_DB_NAME)

        if (dbPath.isNullOrEmpty()) {
            showToast("No database selected")
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = dbName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize DB in ViewModel
        lifecycleScope.launch {
            viewModel.switchDatabase(dbPath)
            // Wait until subjects are loaded (or an error occurs) so that the
            // start panel shows ready-to-use subject/system lists. This avoids
            // showing the panel when the DB hasn't finished initialization.
            try {
                viewModel.state.first { it.subjectsResource !is com.medicalquiz.app.utils.Resource.Loading }
            } catch (_: Exception) { /* ignore */ }
import com.medicalquiz.app.utils.HtmlUtils
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.launchCatching
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
        
                mediaHandler = MediaHandler(this@QuizActivity)
            filterDialogHandler = FilterDialogHandler(this@QuizActivity)
            mediaHandler.reset()
            // DB manager is switched through `viewModel.switchDatabase` during onCreate
            setupWebViews()
            setupDrawer()
            setupListeners()
                // Show Start panel so the user can select filters before starting the quiz
                showStartFiltersPanel()
        }
        

        // Observe unified view state from ViewModel
        // `autoLoadFirstQuestion` controls whether the first question should be
        // loaded when a new filtered list arrives. We default to `false` so
        // DB open does not perform an automatic load (user will filter first).
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousState: com.medicalquiz.app.ui.QuizState? = null
                viewModel.state.collect { state ->
                    // UI updates happen here as state changes
                    updateNavigationControls(state)

                    // Load first question only once
                    if (state.questionIds.isNotEmpty() && autoLoadFirstQuestion) {
                        viewModel.loadQuestion(0)
                        autoLoadFirstQuestion = false
                    }

                    // React to changes in the loaded question id list; keep UI in sync
                    if (previousState?.questionIds != state.questionIds) {
                        if (state.questionIds.isEmpty()) {
                            showToast("No questions found with selected filters")
                            binding.textViewStatus.apply {
                                text = "No questions found for current filters"
                                isVisible = true
                            }
                            updateToolbarSubtitle()
                        } else {
                            binding.textViewStatus.apply {
                                text = "Loaded ${state.questionIds.size} questions"
                                isVisible = true
                            }
                            updateToolbarSubtitle()
                            // New list available — do not auto-load. Let the user
                            // filter and choose when to load. We give a hint in
                            // the status that the user can tap the counter or
                            // use Next to begin.
                            // No auto-start; user will use Start panel or next/counter to begin.
                        }
                    }

                    // Display question when it changes
                    if (previousState?.currentQuestionId != state.currentQuestionId) {
                        displayQuestion(state)
                    }

                    // Update performance text when it changes
                    if (previousState?.currentPerformance != state.currentPerformance) {
                        binding.textViewPerformance.apply {
                            text = buildPerformanceSummary(state.currentPerformance)
                                isVisible = !text.isNullOrBlank() && state.isLoggingEnabled
                        }
                    }

                    // Note: actual media and HTML content is loaded in `displayQuestion` when the question changes

                    previousState = state
                }
            }
        }

        // Collect one-off UI events from ViewModel
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is com.medicalquiz.app.viewmodel.UiEvent.ShowToast -> showToast(event.message)
                        is com.medicalquiz.app.viewmodel.UiEvent.OpenFilterDialog -> {
                            // Activity triggers dialogs; ViewModel requests them via event
                            when (event.type) {
                                com.medicalquiz.app.viewmodel.UiEvent.FilterDialogType.SUBJECTS -> {/* not used */}
                                com.medicalquiz.app.viewmodel.UiEvent.FilterDialogType.SYSTEMS -> {/* not used */}
                            }
                        }
                        is com.medicalquiz.app.viewmodel.UiEvent.OpenMedia -> {
                            mediaHandler.handleMediaLink(event.url)
                        }
                                    is com.medicalquiz.app.viewmodel.UiEvent.ShowAnswer -> {
                                        updateWebViewAnswerState(event.correctAnswerId, event.selectedAnswerId)
                                        val s = viewModel.state.firstMatching()
                                        showQuestionDetails(s)
                        }
                    }
                }
            }
        }
        
        // Restore state if available
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }
        
        // Handle back button press
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
    
    private fun setupWebViews() {
        // Configure webview through controller
        webViewController = com.medicalquiz.app.utils.WebViewController(mediaHandler)
        webViewController.setup(binding.webViewQuestion, object : com.medicalquiz.app.utils.WebViewController.Bridge {
                    override fun onAnswerSelected(answerId: Long) {
                runOnUiThread {
                    // Delegate selection + submission to ViewModel (business logic kept outside Activity)
                    viewModel.onAnswerSelected(answerId)
                        val timeTaken = java.lang.System.currentTimeMillis() - startTime
                        viewModel.submitAnswer(timeTaken)
                }
            }

                    override fun openMedia(mediaRef: String) {
                runOnUiThread {
                    val ref = mediaRef.takeIf { it.isNotBlank() } ?: return@runOnUiThread
                    val url = if (ref.startsWith("file://") || ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("media://")) {
                        ref
                    } else {
                        "file:///media/${ref.substringAfterLast('/') }"
                    }
                    viewModel.openMedia(url)
                }
            }
        })
    }
    
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
    
    private fun setupListeners() {
        setupBottomBarListeners()
    }

    private fun setupBottomBarListeners() {
        binding.buttonPrevious.setOnClickListener { loadPreviousQuestion() }
        binding.buttonNext.setOnClickListener { loadNextQuestion() }
        // Open jump-to dialog when the user taps the counter
        binding.counterContainer.setOnClickListener { showJumpToDialog() }
        binding.counterContainer.contentDescription = "Tap to jump to question"
    }
    
    private fun initializeDatabase(dbPath: String) {
        binding.textViewStatus.text = "Loading database..."
        clearCaches() // Clear caches when switching databases

        // Let ViewModel initialize DB and fetch IDs; let the Activity react to observer
        viewModel.initializeDatabase(dbPath)
    }
    
    private fun loadQuestion(index: Int) {
        viewModel.loadQuestion(index)
        startTime = java.lang.System.currentTimeMillis()
    }
    
    private fun displayQuestion(state: com.medicalquiz.app.ui.QuizState) {
        val question = state.currentQuestion ?: return
        // Question number is no longer shown in the UI

        // Move HTML building and media processing to background thread
        lifecycleScope.launch(Dispatchers.Default) {
            val quizHtml = com.medicalquiz.app.utils.QuestionHtmlBuilder.build(question, state.currentAnswers)
            val mediaFiles = HtmlUtils.collectMediaFiles(question)

            withContext(Dispatchers.Main) {
                WebViewRenderer.loadContent(this@QuizActivity, binding.webViewQuestion, quizHtml)

                // Title label removed from UI — skip rendering

                val metadata = buildString {
                    append("ID: ${question.id}")
                    if (!question.subName.isNullOrBlank()) append(" | Subject: ${question.subName}")
                    if (!question.sysName.isNullOrBlank()) append(" | System: ${question.sysName}")
                }

                binding.textViewMetadata.apply {
                    text = metadata
                    isVisible = false
                }

                binding.textViewPerformance.apply {
                    text = buildPerformanceSummary(state.currentPerformance)
                    isVisible = !text.isNullOrBlank() && state.isLoggingEnabled
                }
                loadPerformanceForQuestion(question.id)

                Log.d(TAG, "Question ${question.id} has media files: $mediaFiles")
                updateMediaInfo(question.id, mediaFiles)

                        viewModel.resetAnswerState()
                updateNavigationControls(state)
            }
        }
    }
    
    private fun submitAnswer() {
        val timeTaken = java.lang.System.currentTimeMillis() - startTime
        viewModel.submitAnswer(timeTaken)
    }

    // buildQuestionHtml has been extracted to `QuestionHtmlBuilder` to improve testability

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int) {
        webViewController.applyAnswerState(binding.webViewQuestion, correctAnswerId, selectedAnswerId)
    }

    // WebView setup is moved to WebViewController

    // JS bridge is provided via WebViewController
    
    private fun loadNextQuestion() {
        viewModel.loadNext()
    }
    
    private fun loadPreviousQuestion() {
        viewModel.loadPrevious()
    }

    private fun showJumpToDialog() {
        // collect a snapshot of state in a coroutine to avoid direct state.value usage
        lifecycleScope.launch {
            val s = viewModel.state.firstMatching()
            // No-op if no questions available
            if (s.questionIds.isEmpty()) return@launch

            val picker = android.widget.NumberPicker(this@QuizActivity).apply {
                minValue = 1
                maxValue = s.questionIds.size
                value = s.currentQuestionIndex + 1
                wrapSelectorWheel = false
            }

            AlertDialog.Builder(this@QuizActivity)
                .setTitle("Jump to question")
                .setView(picker)
                .setPositiveButton("Go") { _, _ ->
                    loadQuestion(picker.value - 1)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showStartFiltersPanel() {
        val panel = binding.startFiltersPanel
        panel.isVisible = true

        // Use global helper functions to update the panel labels. This avoids
        // duplication between the local and global implementations.

        panel.findViewById<android.widget.Button>(R.id.buttonSelectSubjectsPanel).setOnClickListener {
            viewModel.fetchSubjects()
            lifecycleScope.launch {
                val resource = viewModel.state.first { it.subjectsResource !is com.medicalquiz.app.utils.Resource.Loading }.subjectsResource
                when (resource) {
                    is com.medicalquiz.app.utils.Resource.Success<List<Subject>> -> {
                        val ss = viewModel.state.firstMatching()
                        filterDialogHandler.showSubjectSelectionDialogSilently(resource.data, ss.selectedSubjectIds, viewModel) {
                            // When subjects are applied, systems list may change — prefetch
                            viewModel.fetchSystemsForSubjects(viewModel.state.value.selectedSubjectIds.toList())
                            updateSubjectLabel()
                            updateSystemLabel()
                            updatePreviewCount()
                        }
                    }
                    is com.medicalquiz.app.utils.Resource.Error -> showToast("Error fetching subjects: ${resource.message}")
                    else -> { /* ignore loading */ }
                }
            }
        }

        panel.findViewById<android.widget.Button>(R.id.buttonSelectSystemsPanel).setOnClickListener {
            val ss = viewModel.state.value
            viewModel.fetchSystemsForSubjects(ss.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList())
            lifecycleScope.launch {
                val resource = viewModel.state.first { it.systemsResource !is com.medicalquiz.app.utils.Resource.Loading }.systemsResource
                when (resource) {
                    is com.medicalquiz.app.utils.Resource.Success<List<System>> -> {
                        filterDialogHandler.showSystemSelectionDialogSilently(resource.data, viewModel.state.value.selectedSystemIds, viewModel) {
                            updateSystemLabel()
                            updatePreviewCount()
                        }
                    }
                    is com.medicalquiz.app.utils.Resource.Error -> showToast("Error fetching systems: ${resource.message}")
                    else -> { }
                }
            }
        }

        panel.findViewById<android.widget.Button>(R.id.buttonSelectPerformancePanel).setOnClickListener {
            val filters = PerformanceFilter.values()
            val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
            val currentIndex = filters.indexOf(viewModel.state.value.performanceFilter)
            AlertDialog.Builder(this@QuizActivity)
                .setTitle("Performance Filter")
                .setSingleChoiceItems(labels, currentIndex) { d, which ->
                    d.dismiss()
                    viewModel.setPerformanceFilter(filters[which])
                    updatePerformanceLabel()
                    updatePreviewCount()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        panel.findViewById<android.widget.Button>(R.id.buttonCancelPanel).setOnClickListener {
            panel.isVisible = false
            updateToolbarSubtitle()
        }

        panel.findViewById<android.widget.Button>(R.id.buttonStartPanel).setOnClickListener {
            // When starting, fetch the filtered question ids and then begin the quiz
            lifecycleScope.launch {
                try {
                    val ids = viewModel.fetchFilteredQuestionIds()
                    viewModel.setQuestionIds(ids)
                    if (ids.isNotEmpty()) {
                        viewModel.loadQuestion(0)
                    } else {
                        binding.textViewStatus.text = "No questions found for current filters"
                    }
                } catch (e: Exception) {
                    showToast("Failed to start: ${e.message}")
                } finally {
                    panel.isVisible = false
                }
            }
        }

        updateSubjectLabel()
        updateSystemLabel()
        updatePerformanceLabel()
        updatePreviewCount()
    }
        // start filters are shown inline via `startFiltersPanel` (no dialog)

        // Update labels with current selection counts
        fun updateSubjectLabel() {
            val s = viewModel.state.value
            val count = s.selectedSubjectIds.size
            binding.startFiltersPanel.findViewById<android.widget.Button>(R.id.buttonSelectSubjectsPanel).text = if (count == 0) "Select Subjects" else "Subjects: $count"
        }

        fun updateSystemLabel() {
            val s = viewModel.state.value
            val count = s.selectedSystemIds.size
            binding.startFiltersPanel.findViewById<android.widget.Button>(R.id.buttonSelectSystemsPanel).text = if (count == 0) "Select Systems" else "Systems: $count"
        }

        fun updatePerformanceLabel() {
            val pf = viewModel.state.value.performanceFilter
            binding.startFiltersPanel.findViewById<android.widget.Button>(R.id.buttonSelectPerformancePanel).text = getPerformanceFilterLabel(pf)
        }

        // Update preview of how many questions match the current filters
        fun updatePreviewCount() {
            lifecycleScope.launch {
                try {
                    val count = viewModel.fetchFilteredQuestionIds().size
                    binding.startFiltersPanel.findViewById<android.widget.TextView>(R.id.textViewPreviewCountPanel).text = if (count == 1) "1 question matches" else "$count questions match"
                    binding.startFiltersPanel.findViewById<android.widget.Button>(R.id.buttonStartPanel).isEnabled = count > 0
                } catch (e: Exception) {
                    binding.startFiltersPanel.findViewById<android.widget.TextView>(R.id.textViewPreviewCountPanel).text = "Preview unavailable"
                    binding.startFiltersPanel.findViewById<android.widget.Button>(R.id.buttonStartPanel).isEnabled = true
                }
            }
        }

        // Dialog-based start flow has been replaced with inline `startFiltersPanel`.
        // Initialization above already set the labels and preview.
    }

    private fun updateNavigationControls(state: com.medicalquiz.app.ui.QuizState) {
        val total = state.questionIds.size
        binding.buttonNext.isEnabled = state.currentQuestionIndex < total - 1
        binding.buttonPrevious.isEnabled = state.currentQuestionIndex > 0
        binding.textViewQuestionIndex.text = (state.currentQuestionIndex + 1).toString()
        binding.textViewTotalQuestions.text = "/ $total"
                    // resource is filled above after fetch
                    when (resource) {
                        is com.medicalquiz.app.utils.Resource.Loading -> { /* optional loading */ }
                        is com.medicalquiz.app.utils.Resource.Success<List<System>> -> {
                                        val systems = resource.data
                                        val ss2 = viewModel.state.firstMatching()
                                        val selectedSystems = ss2.selectedSystemIds
                                        filterDialogHandler.showSystemSelectionDialog(systems, selectedSystems, viewModel)
                        }
                        is com.medicalquiz.app.utils.Resource.Error -> showToast("Error fetching systems: ${resource.message}")
                    }
                }
            }
            R.id.nav_filter_performance -> showPerformanceFilterDialog()
            R.id.nav_clear_filters -> clearFilters()
            R.id.nav_settings -> showSettingsDialog()
            R.id.nav_about -> showToast("About coming soon")
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun clearFilters() {
        viewModel.applySelectedSubjects(emptySet())
        viewModel.setSelectedSystems(emptySet())
        viewModel.setPerformanceFilter(PerformanceFilter.ALL)
        settingsRepository.setPerformanceFilter(PerformanceFilter.ALL)
        // Clearing filters should not auto-start the quiz from drawer.
        reloadQuestionsWithFilters()
    }

    private fun showPerformanceFilterDialog() {
        val filters = PerformanceFilter.values()
        val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
        lifecycleScope.launch {
            val s = viewModel.state.firstMatching()
            val currentIndex = filters.indexOf(s.performanceFilter)

        AlertDialog.Builder(this@QuizActivity)
            .setTitle("Performance Filter")
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    dialog.dismiss()
                    if (s.performanceFilter == filters[which]) return@setSingleChoiceItems
                    applyPerformanceFilter(filters[which])
            }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun applyPerformanceFilter(filter: PerformanceFilter) {
        viewModel.setPerformanceFilter(filter)
                settingsRepository.setPerformanceFilter(filter)
        reloadQuestionsWithFilters()
    }
    
    private fun reloadQuestionsWithFilters() {
        // Delegate DB loading to ViewModel and rely on state collector to update the UI
        viewModel.loadFilteredQuestionIds()
    }

    private suspend fun fetchFilteredQuestionIds(): List<Long> = viewModel.fetchFilteredQuestionIds()
    
    private fun updateToolbarSubtitle() {
        supportActionBar?.subtitle = null
    }

    private fun getPerformanceFilterLabel(filter: PerformanceFilter): String = when (filter) {
        PerformanceFilter.ALL -> "All Questions"
        PerformanceFilter.UNANSWERED -> "Not Attempted"
        PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
        PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
        PerformanceFilter.EVER_CORRECT -> "Ever Correct"
        PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
    }

    // System pruning is handled by the ViewModel now

    private fun buildPerformanceSummary(performance: QuestionPerformance?): String {
        val summary = performance?.takeIf { it.attempts > 0 } ?: return ""
        val lastLabel = if (summary.lastCorrect) "Correct" else "Incorrect"
        return "Attempts: ${summary.attempts} | Last: $lastLabel"
    }

    private fun clearCaches() {
        cacheManager.clearCaches()
        HtmlUtils.clearMediaCaches()
    }

    // Cache trimming is now performed by CacheManager in the ViewModel

    // performance is now tracked in ViewModel state — no local cache required

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
    }
    
    private fun restoreInstanceState(savedInstanceState: Bundle) {
        // Restore quiz state
        restoreQuizState(savedInstanceState)
        
        val dbPath = intent.getStringExtra(EXTRA_DB_PATH) ?: return
        
        // Initialize database and restore question state
        binding.textViewStatus.text = "Restoring quiz state..."
        
        launchCatching(
            block = {
                // previously: databaseManager = MedicalQuizApp.switchDatabase(dbPath)
                // Now handled by ViewModel.switchDatabase for better separation of concerns
                mediaHandler.reset()
                // FilterDialogHandler uses ViewModel for DB access; ensure ViewModel has DB set
                // DB manager is now switched via ViewModel
                
                // Restore current question if available — rehydrate into ViewModel state
                val questionId = savedInstanceState.getLong(STATE_CURRENT_QUESTION_ID, -1)
                if (questionId != -1L) {
                    // Let the ViewModel restore DB state for the given question id
                    viewModel.restoreQuestionFromDatabase(questionId)
                }
            },
            onSuccess = {
                // Offload any suspend or reactive reads to a coroutine to avoid synchronous .value reads
                lifecycleScope.launch {
                    val currentState = viewModel.state.firstMatching()
                    if (currentState.questionIds.isEmpty()) {
                        try {
                            val ids = fetchFilteredQuestionIds()
                            // questionIds stored in ViewModel state
                            val s2 = viewModel.state.firstMatching()
                            if (s2.questionIds.isNotEmpty()) {
                                binding.textViewStatus.apply {
                                    text = "Loaded ${s2.questionIds.size} questions"
                                    isVisible = true
                                }
                                updateToolbarSubtitle()
                                loadQuestion(0)
                            } else {
                                binding.textViewStatus.apply {
                                    text = "No questions found for current filters"
                                    isVisible = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch questions during restore", e)
                        }
                    } else {
                    // Restore the current question display from VM state
                    val s = viewModel.state.firstMatching()
                    if (s.currentQuestion != null) {
                        displayQuestion(s)
                        loadPerformanceForQuestion(s.currentQuestion!!.id)
                    } else {
                        val s3 = viewModel.state.firstMatching()
                        if (s3.questionIds.isNotEmpty()) loadQuestion(s3.currentQuestionIndex)
                    }
                    updateToolbarSubtitle()
                    binding.textViewStatus.apply {
                        text = "Loaded ${s.questionIds.size} questions"
                        isVisible = true
                    }
                }
                }
            },
            onFailure = { throwable ->
                binding.textViewStatus.text = "Error restoring state: ${throwable.message}"
                Log.e(TAG, "Failed to restore instance state", throwable)
                // Fall back to fresh initialization
                initializeDatabase(dbPath)
            }
        )
    }

    private fun restoreQuizState(savedInstanceState: Bundle) {
        val currentIndex = savedInstanceState.getInt(STATE_CURRENT_QUESTION_INDEX, 0)
        val ids = savedInstanceState.getLongArray(STATE_QUESTION_IDS)?.toList() ?: emptyList()
        val subjects = savedInstanceState.getLongArray(STATE_SELECTED_SUBJECT_IDS)?.toList() ?: emptyList()
        val systems = savedInstanceState.getLongArray(STATE_SELECTED_SYSTEM_IDS)?.toList() ?: emptyList()
        viewModel.setSelectedSubjects(subjects.toSet())
        viewModel.setSelectedSystems(systems.toSet())
        val perf = savedInstanceState.getString(STATE_PERFORMANCE_FILTER)?.let { PerformanceFilter.valueOf(it) } ?: PerformanceFilter.ALL
        viewModel.setPerformanceFilter(perf)

        viewModel.setQuestionIds(ids)
        if (currentIndex >= 0 && currentIndex < ids.size) viewModel.loadQuestion(currentIndex)

        // restore answer submission state into ViewModel
        val wasSubmitted = savedInstanceState.getBoolean(STATE_ANSWER_SUBMITTED, false)
        val selectedAnswer = if (savedInstanceState.containsKey(STATE_SELECTED_ANSWER_ID)) savedInstanceState.getInt(STATE_SELECTED_ANSWER_ID) else null
        viewModel.setAnswerSubmissionState(wasSubmitted, selectedAnswer)

        // restore test id
        val savedTestId = savedInstanceState.getString(STATE_TEST_ID)
        if (!savedTestId.isNullOrBlank()) viewModel.setTestId(savedTestId)
    }
    
    override fun onPause() {
        super.onPause()
        // Flush logs when app goes to background — let ViewModel manage DB flush
        viewModel.flushLogsIfEnabledOnPause()
// Keep ViewModel as single authority for log management; Activity no longer directly manages logs
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Close database (will auto-flush remaining logs)
        // Let ViewModel close the database so it can keep DB logic consolidated
        viewModel.closeDatabase()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        saveQuizState(outState)
    }

    private fun saveQuizState(outState: android.os.Bundle) {
        val s = viewModel.state.value
        outState.putInt(STATE_CURRENT_QUESTION_INDEX, s.currentQuestionIndex)
        outState.putLongArray(STATE_QUESTION_IDS, s.questionIds.toLongArray())
        outState.putLongArray(STATE_SELECTED_SUBJECT_IDS, s.selectedSubjectIds.takeIf { it.isNotEmpty() }?.map { it }?.toLongArray() ?: longArrayOf())
        outState.putLongArray(STATE_SELECTED_SYSTEM_IDS, s.selectedSystemIds.takeIf { it.isNotEmpty() }?.map { it }?.toLongArray() ?: longArrayOf())
        outState.putString(STATE_PERFORMANCE_FILTER, s.performanceFilter.name)
        outState.putString(STATE_TEST_ID, viewModel.getTestId())
        outState.putLong(STATE_START_TIME, startTime)
        outState.putBoolean(STATE_ANSWER_SUBMITTED, s.answerSubmitted)
        s.selectedAnswerId?.let { outState.putInt(STATE_SELECTED_ANSWER_ID, it) }
        outState.putLong(STATE_CURRENT_QUESTION_ID, s.currentQuestion?.id ?: -1L)
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

    private fun showQuestionDetails(state: com.medicalquiz.app.ui.QuizState) {
        val previousScrollY = binding.scrollViewContent.scrollY

        // textViewTitle removed — nothing to toggle here
        binding.textViewMetadata.isVisible = !binding.textViewMetadata.text.isNullOrBlank()
        binding.textViewPerformance.isVisible = !binding.textViewPerformance.text.isNullOrBlank() && state.isLoggingEnabled
        binding.textViewMediaInfo.isVisible = !binding.textViewMediaInfo.text.isNullOrBlank()

        binding.scrollViewContent.post {
            binding.scrollViewContent.scrollTo(0, previousScrollY)
        }
    }

    private fun loadPerformanceForQuestion(questionId: Long) {
        // Delegate to ViewModel — it will update `state.currentPerformance`
        viewModel.loadPerformanceForQuestion(questionId)
    }

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
            AlertDialog.Builder(this@QuizActivity)
                .setTitle("Reset log history")
                .setMessage("This will permanently delete all stored answer logs. Continue?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try {
                                    viewModel.clearLogsFromDb()
                            showToast("Logs cleared")
                        } catch (e: Exception) {
                            showToast("Failed to clear logs: ${e.message}")
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun getMimeType(fileName: String): String {
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "")) 
            ?: "application/octet-stream"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "QuizActivity"
        private const val EXTRA_DB_PATH = "DB_PATH"
        private const val EXTRA_DB_NAME = "DB_NAME"
        
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
}
