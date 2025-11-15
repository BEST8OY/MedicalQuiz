package com.medicalquiz.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.activity.viewModels
import com.google.android.material.navigation.NavigationView
import com.medicalquiz.app.MedicalQuizApp
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.databinding.DialogSettingsBinding
import com.medicalquiz.app.ui.FilterDialogHandler
import com.medicalquiz.app.ui.MediaHandler
import com.medicalquiz.app.utils.HtmlUtils
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.launchCatching
import com.medicalquiz.app.utils.safeEvaluateJavascript
import com.medicalquiz.app.utils.safeLoadDataWithBaseURL
import com.medicalquiz.app.settings.SettingsManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import java.util.UUID

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityQuizBinding
    private lateinit var databaseManager: DatabaseManager
    private val viewModel: com.medicalquiz.app.viewmodel.QuizViewModel by androidx.activity.viewModels()
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mediaHandler: MediaHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    private lateinit var webViewController: com.medicalquiz.app.utils.WebViewController
    private lateinit var settingsManager: SettingsManager
    
    private var questionIds: List<Long> = emptyList()
    private var currentQuestionIndex = 0
    private var currentQuestion: Question? = null
    private var currentAnswers: List<Answer> = emptyList()
    private var selectedAnswerId: Int? = null
    private var testId = UUID.randomUUID().toString()
    private var startTime: Long = 0
    // Selected filters are now stored in the ViewModel
    // Performance filter moved to ViewModel
    private var answerSubmitted = false
    private val mediaFilesCache = mutableMapOf<Long, List<String>>()
    private var currentPerformance: QuestionPerformance? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollViewContent.clipToPadding = false
        applyWindowInsets()
        settingsManager = SettingsManager(this)
        val initialPerformance = PerformanceFilter.values().firstOrNull {
            it.storageValue == settingsManager.performanceFilterValue
        } ?: PerformanceFilter.ALL
        viewModel.setPerformanceFilter(initialPerformance)
        
        val dbPath = intent.getStringExtra(EXTRA_DB_PATH)
        val dbName = intent.getStringExtra(EXTRA_DB_NAME)
        
        if (dbPath == null) {
            showToast("No database selected")
            finish()
            return
        }
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = dbName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // initialize DB in ViewModel and keep local reference for non-ViewModel helpers
        // Switch DB at the app level then hand it to the ViewModel (suspend function)
        lifecycleScope.launch {
            databaseManager = MedicalQuizApp.switchDatabase(dbPath)
            mediaHandler = MediaHandler(this@QuizActivity)
            filterDialogHandler = FilterDialogHandler(this@QuizActivity, lifecycleScope, viewModel)
            mediaHandler.reset()
            viewModel.setDatabaseManager(databaseManager)

            // setup UI now that DB is ready
            setupWebViews()
            setupDrawer()
            setupListeners()
        }
        // Note: setupWebViews, setupDrawer, setupListeners will run after DB initialization

        // Observe question events from ViewModel
        viewModel.currentQuestion.observe(this) { question ->
            currentQuestion = question
            displayQuestion()
        }
        viewModel.currentAnswers.observe(this) { answers ->
            currentAnswers = answers
        }
        var autoLoadFirstQuestion = savedInstanceState == null
        viewModel.questionIds.observe(this) { ids ->
            questionIds = ids
            if (ids.isNotEmpty() && autoLoadFirstQuestion) {
                viewModel.loadQuestion(0)
                autoLoadFirstQuestion = false
            }
        }
        viewModel.currentQuestionIndex.observe(this) { index ->
            currentQuestionIndex = index
            updateNavigationControls()
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
                    if (answerSubmitted) return@runOnUiThread
                    val answer = currentAnswers.firstOrNull { it.answerId == answerId } ?: return@runOnUiThread
                    selectedAnswerId = answer.answerId.toInt()
                    submitAnswer()
                }
            }

            override fun openMedia(mediaRef: String) {
                runOnUiThread {
                    val ref = mediaRef.takeIf { it.isNotBlank() } ?: return@runOnUiThread
                    val url = if (ref.startsWith("file://") || ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("media://")) {
                        ref
                    } else {
                        "file:///media/${ref.substringAfterLast('/')}"
                    }
                    mediaHandler.handleMediaLink(url)
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
        trimCachesPeriodically()
        viewModel.loadQuestion(index)
        startTime = System.currentTimeMillis()
    }
    
    private fun displayQuestion() {
        val question = currentQuestion ?: return

        // Question number is no longer shown in the UI

        // Move HTML building and media processing to background thread
        lifecycleScope.launch(Dispatchers.Default) {
            val quizHtml = com.medicalquiz.app.utils.QuestionHtmlBuilder.build(question, currentAnswers)
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
                    text = buildPerformanceSummary(currentPerformance)
                    isVisible = false
                }
                loadPerformanceForQuestion(question.id)

                Log.d(TAG, "Question ${question.id} has media files: $mediaFiles")
                updateMediaInfo(question.id, mediaFiles)

                answerSubmitted = false
                selectedAnswerId = null
                updateNavigationControls()
            }
        }
    }
    
    private fun submitAnswer() {
        val answerId = selectedAnswerId
        if (answerId == null) {
            showToast("Please select an answer")
            return
        }
        
        if (answerSubmitted) return
        answerSubmitted = true
        
        val question = currentQuestion ?: return
        val timeTaken = System.currentTimeMillis() - startTime
        
        launchCatching(
            block = {
                if (settingsManager.isLoggingEnabled) {
                    databaseManager.logAnswer(
                        qid = question.id,
                        selectedAnswer = answerId,
                        corrAnswer = question.corrAns,
                        time = timeTaken,
                        testId = testId
                    )
                    
                    // Only update performance cache when logging is enabled
                    val correctAnswer = currentAnswers.getOrNull(question.corrAns - 1)
                    val normalizedCorrectId = correctAnswer?.answerId?.toInt() ?: -1
                    val wasCorrect = normalizedCorrectId == answerId
                    updateLocalPerformanceCache(question.id, wasCorrect)
                }
                
                // UI updates must happen on main thread
                withContext(Dispatchers.Main) {
                    val correctAnswer = currentAnswers.getOrNull(question.corrAns - 1)
                    val normalizedCorrectId = correctAnswer?.answerId?.toInt() ?: -1
                    updateWebViewAnswerState(normalizedCorrectId, answerId)
                    showQuestionDetails()
                }
            },
            onFailure = { throwable ->
                showToast("Error saving answer: ${throwable.message}")
            }
        )
    }

    // buildQuestionHtml has been extracted to `QuestionHtmlBuilder` to improve testability

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int) {
        webViewController.applyAnswerState(binding.webViewQuestion, correctAnswerId, selectedAnswerId)
    }

    // WebView setup is moved to WebViewController

    // JS bridge is provided via WebViewController
    }
    
    private fun loadNextQuestion() {
        val nextIndex = currentQuestionIndex + 1
        if (questionIds.getOrNull(nextIndex) != null) {
            loadQuestion(nextIndex)
        }
    }
    
    private fun loadPreviousQuestion() {
        val previousIndex = currentQuestionIndex - 1
        if (questionIds.getOrNull(previousIndex) != null) {
            loadQuestion(previousIndex)
        }
    }

    private fun showJumpToDialog() {
        // No-op if no questions available
        if (questionIds.isEmpty()) return

        val picker = android.widget.NumberPicker(this).apply {
            minValue = 1
            maxValue = questionIds.size
            value = currentQuestionIndex + 1
            wrapSelectorWheel = false
        }

        AlertDialog.Builder(this)
            .setTitle("Jump to question")
            .setView(picker)
            .setPositiveButton("Go") { _, _ ->
                loadQuestion(picker.value - 1)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateNavigationControls() {
        val total = questionIds.size
        binding.buttonNext.isEnabled = currentQuestionIndex < total - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
        binding.textViewQuestionIndex.text = (currentQuestionIndex + 1).toString()
        binding.textViewTotalQuestions.text = "/ $total"
        binding.counterContainer.isEnabled = total > 0
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_filter_subject -> filterDialogHandler.showSubjectFilterDialog(viewModel.selectedSubjectIds.value ?: emptySet()) { subjectIds ->
                lifecycleScope.launch {
                    viewModel.setSelectedSubjects(subjectIds)
                    // Ensure system filter stays valid for the new subject set
                    val validSystems = viewModel.pruneInvalidSystems().toSet()
                    viewModel.setSelectedSystems(validSystems)
                    reloadQuestionsWithFilters()
                }
            }
            R.id.nav_filter_system -> filterDialogHandler.showSystemFilterDialog(this, viewModel.selectedSystemIds.value ?: emptySet(), viewModel.selectedSubjectIds.value ?: emptySet()) { systemIds ->
                viewModel.setSelectedSystems(systemIds)
                reloadQuestionsWithFilters()
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
        viewModel.setSelectedSubjects(emptySet())
        viewModel.setSelectedSystems(emptySet())
        viewModel.setPerformanceFilter(PerformanceFilter.ALL)
        settingsManager.performanceFilterValue = PerformanceFilter.ALL.storageValue
        reloadQuestionsWithFilters()
    }

    private fun showPerformanceFilterDialog() {
        val filters = PerformanceFilter.values()
        val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
        val currentIndex = filters.indexOf(viewModel.performanceFilter.value ?: PerformanceFilter.ALL)

        AlertDialog.Builder(this)
            .setTitle("Performance Filter")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                dialog.dismiss()
                applyPerformanceFilter(filters[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyPerformanceFilter(filter: PerformanceFilter) {
        if (viewModel.performanceFilter.value == filter) return
        viewModel.setPerformanceFilter(filter)
        settingsManager.performanceFilterValue = filter.storageValue
        reloadQuestionsWithFilters()
    }
    
    private fun reloadQuestionsWithFilters() {
        launchCatching(
            block = { fetchFilteredQuestionIds() },
            onSuccess = { ids ->
                viewModel.setQuestionIds(ids)
                if (questionIds.isEmpty()) {
                    showToast("No questions found with selected filters")
                    binding.textViewStatus.text = "No questions found for current filters"
                    updateToolbarSubtitle()
                } else {
                    binding.textViewStatus.text = "Loaded ${ids.size} questions"
                    updateToolbarSubtitle()
                    viewModel.loadQuestion(0)
                }
            },
            onFailure = { throwable ->
                Log.e(TAG, "Error reloading questions", throwable)
                showToast("Error reloading questions: ${throwable.message}")
            }
        )
    }

    private suspend fun fetchFilteredQuestionIds(): List<Long> {
        val subjectFilter = viewModel.selectedSubjectIds.value?.takeIf { it.isNotEmpty() }?.toList()
        val systemFilter = viewModel.selectedSystemIds.value?.takeIf { it.isNotEmpty() }?.toList()
        return viewModel.getDatabaseManager()?.getQuestionIds(
            subjectIds = subjectFilter,
            systemIds = systemFilter,
            performanceFilter = viewModel.performanceFilter.value ?: PerformanceFilter.ALL
        )
    }
    
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
        mediaFilesCache.clear()
        HtmlUtils.clearMediaCaches()
    }

    private fun trimCachesPeriodically() {
        // Trim caches every 50 questions to prevent memory bloat
        if (currentQuestionIndex > 0 && currentQuestionIndex % 50 == 0) {
            HtmlUtils.trimCaches()
        }
    }

    private fun updateLocalPerformanceCache(questionId: Long, wasCorrect: Boolean) {
        currentPerformance = currentPerformance?.let { existing ->
            existing.copy(
                lastCorrect = wasCorrect,
                everCorrect = existing.everCorrect || wasCorrect,
                everIncorrect = existing.everIncorrect || !wasCorrect,
                attempts = existing.attempts + 1
            )
        } ?: QuestionPerformance(
            qid = questionId,
            lastCorrect = wasCorrect,
            everCorrect = wasCorrect,
            everIncorrect = !wasCorrect,
            attempts = 1
        )
        // UI update moved to caller to ensure proper threading
    }

    private fun updateMediaInfo(questionId: Long, mediaFiles: List<String>) {
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
                databaseManager = MedicalQuizApp.switchDatabase(dbPath)
                mediaHandler.reset()
                // FilterDialogHandler uses ViewModel for DB access; ensure ViewModel has DB set
                viewModel.setDatabaseManager(databaseManager)
                
                // Restore current question if available
                val questionId = savedInstanceState.getLong(STATE_CURRENT_QUESTION_ID, -1)
                if (questionId != -1L) {
                    currentQuestion = databaseManager.getQuestionById(questionId)
                    currentAnswers = databaseManager.getAnswersForQuestion(questionId)
                }
            },
            onSuccess = {
                if (questionIds.isEmpty()) {
                    // If no questions were saved, fetch them using launchCatching
                    // to keep consistent error handling and logging
                    launchCatching(
                        block = { fetchFilteredQuestionIds() },
                        onSuccess = { ids ->
                            questionIds = ids
                            if (questionIds.isNotEmpty()) {
                                binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
                                updateToolbarSubtitle()
                                loadQuestion(0)
                            } else {
                                binding.textViewStatus.text = "No questions found for current filters"
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to fetch questions during restore", e)
                        }
                    )
                } else {
                    // Restore the current question display
                    if (currentQuestion != null) {
                        displayQuestion()
                        loadPerformanceForQuestion(currentQuestion!!.id)
                    } else if (questionIds.isNotEmpty()) {
                        loadQuestion(currentQuestionIndex)
                    }
                    updateToolbarSubtitle()
                    binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
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
        currentQuestionIndex = savedInstanceState.getInt(STATE_CURRENT_QUESTION_INDEX, 0)
        questionIds = savedInstanceState.getLongArray(STATE_QUESTION_IDS)?.toList() ?: emptyList()
        val subjects = savedInstanceState.getLongArray(STATE_SELECTED_SUBJECT_IDS)?.toList() ?: emptyList()
        val systems = savedInstanceState.getLongArray(STATE_SELECTED_SYSTEM_IDS)?.toList() ?: emptyList()
        viewModel.setSelectedSubjects(subjects.toSet())
        viewModel.setSelectedSystems(systems.toSet())
        val perf = savedInstanceState.getString(STATE_PERFORMANCE_FILTER)?.let { PerformanceFilter.valueOf(it) } ?: PerformanceFilter.ALL
        viewModel.setPerformanceFilter(perf)
        testId = savedInstanceState.getString(STATE_TEST_ID) ?: UUID.randomUUID().toString()
        startTime = savedInstanceState.getLong(STATE_START_TIME, System.currentTimeMillis())
        answerSubmitted = savedInstanceState.getBoolean(STATE_ANSWER_SUBMITTED, false)
        selectedAnswerId = if (savedInstanceState.containsKey(STATE_SELECTED_ANSWER_ID)) {
            savedInstanceState.getInt(STATE_SELECTED_ANSWER_ID)
        } else null
    }
    
    override fun onPause() {
        super.onPause()
        // Flush logs when app goes to background
        if (settingsManager.isLoggingEnabled) {
            lifecycleScope.launch {
                try {
                    val flushed = databaseManager.flushLogs()
                    if (flushed > 0) {
                        println("Flushed $flushed logs on pause")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            databaseManager.clearPendingLogsBuffer()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Close database (will auto-flush remaining logs)
        lifecycleScope.launch {
            try {
                databaseManager.closeDatabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        saveQuizState(outState)
    }

    private fun saveQuizState(outState: android.os.Bundle) {
        outState.putInt(STATE_CURRENT_QUESTION_INDEX, currentQuestionIndex)
        outState.putLongArray(STATE_QUESTION_IDS, questionIds.toLongArray())
        outState.putLongArray(STATE_SELECTED_SUBJECT_IDS, viewModel.selectedSubjectIds.value?.toLongArray() ?: longArrayOf())
        outState.putLongArray(STATE_SELECTED_SYSTEM_IDS, viewModel.selectedSystemIds.value?.toLongArray() ?: longArrayOf())
        outState.putString(STATE_PERFORMANCE_FILTER, viewModel.performanceFilter.value?.name ?: PerformanceFilter.ALL.name)
        outState.putString(STATE_TEST_ID, testId)
        outState.putLong(STATE_START_TIME, startTime)
        outState.putBoolean(STATE_ANSWER_SUBMITTED, answerSubmitted)
        selectedAnswerId?.let { outState.putInt(STATE_SELECTED_ANSWER_ID, it) }
        outState.putLong(STATE_CURRENT_QUESTION_ID, currentQuestion?.id ?: -1L)
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

    private fun showQuestionDetails() {
        val previousScrollY = binding.scrollViewContent.scrollY

        // textViewTitle removed — nothing to toggle here
        binding.textViewMetadata.isVisible = !binding.textViewMetadata.text.isNullOrBlank()
        binding.textViewPerformance.isVisible = !binding.textViewPerformance.text.isNullOrBlank() && settingsManager.isLoggingEnabled
        binding.textViewMediaInfo.isVisible = !binding.textViewMediaInfo.text.isNullOrBlank()

        binding.scrollViewContent.post {
            binding.scrollViewContent.scrollTo(0, previousScrollY)
        }
    }

    private fun loadPerformanceForQuestion(questionId: Long) {
        if (!settingsManager.isLoggingEnabled) {
            // Don't load performance data if logging is disabled
            currentPerformance = null
            binding.textViewPerformance.text = ""
            return
        }

        launchCatching(
            block = { databaseManager.getQuestionPerformance(questionId) },
            onSuccess = { performance ->
                currentPerformance = performance
                binding.textViewPerformance.text = buildPerformanceSummary(performance)
            },
            onFailure = { throwable ->
                currentPerformance = null
                Log.w(TAG, "Unable to load performance for question $questionId", throwable)
                binding.textViewPerformance.text = ""
            }
        )
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.switchLogAnswers.isChecked = settingsManager.isLoggingEnabled

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.switchLogAnswers.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isLoggingEnabled = isChecked
            if (!isChecked) {
                databaseManager.clearPendingLogsBuffer()
            }
        }

        dialogBinding.buttonResetLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset log history")
                .setMessage("This will permanently delete all stored answer logs. Continue?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            databaseManager.clearLogs()
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
