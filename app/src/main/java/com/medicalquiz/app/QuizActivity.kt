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
import java.util.UUID

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityQuizBinding
    private lateinit var databaseManager: DatabaseManager
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mediaHandler: MediaHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    private lateinit var quizWebInterface: QuizWebInterface
    private lateinit var settingsManager: SettingsManager
    
    private var questionIds: List<Long> = emptyList()
    private var currentQuestionIndex = 0
    private var currentQuestion: Question? = null
    private var currentAnswers: List<Answer> = emptyList()
    private var selectedAnswerId: Int? = null
    private val testId = UUID.randomUUID().toString()
    private var startTime: Long = 0
    private val selectedSubjectIds = mutableSetOf<Long>()
    private val selectedSystemIds = mutableSetOf<Long>()
    private var performanceFilter: PerformanceFilter = PerformanceFilter.ALL
    private var answerSubmitted = false
    private var currentPerformance: QuestionPerformance? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollViewContent.clipToPadding = false
        applyWindowInsets()
        settingsManager = SettingsManager(this)
        performanceFilter = PerformanceFilter.values().firstOrNull {
            it.storageValue == settingsManager.performanceFilterValue
        } ?: PerformanceFilter.ALL
        
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
        
        databaseManager = DatabaseManager(dbPath)
        mediaHandler = MediaHandler(this)
        filterDialogHandler = FilterDialogHandler(this, lifecycleScope, databaseManager)
        
        setupWebViews()
        setupDrawer()
        setupListeners()
        
        // Restore state if available
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        } else {
            initializeDatabase(dbPath)
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
        quizWebInterface = QuizWebInterface()
        configureWebView(binding.webViewQuestion)
        binding.webViewQuestion.addJavascriptInterface(quizWebInterface, "AndroidBridge")
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
        binding.buttonNext.setOnClickListener {
            loadNextQuestion()
        }
        
        binding.buttonPrevious.setOnClickListener {
            loadPreviousQuestion()
        }
    }
    
    private fun initializeDatabase(dbPath: String) {
        binding.textViewStatus.text = "Loading database..."
        
        launchCatching(
            block = {
                databaseManager = MedicalQuizApp.switchDatabase(dbPath)
                mediaHandler.reset()
                filterDialogHandler.updateDatabaseManager(databaseManager)
                fetchFilteredQuestionIds()
            },
            onSuccess = { ids ->
                questionIds = ids
                if (questionIds.isEmpty()) {
                    binding.textViewStatus.text = "No questions found for current filters"
                    updateToolbarSubtitle()
                } else {
                    binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
                    updateToolbarSubtitle()
                    loadQuestion(0)
                }
            },
            onFailure = { throwable ->
                binding.textViewStatus.text = "Error: ${throwable.message}"
                Log.e(TAG, "Failed to initialize database", throwable)
            }
        )
    }
    
    private fun loadQuestion(index: Int) {
        val questionId = questionIds.getOrNull(index) ?: return
        currentQuestionIndex = index

        launchCatching(
            block = {
                currentPerformance = null
                currentQuestion = databaseManager.getQuestionById(questionId)
                currentAnswers = databaseManager.getAnswersForQuestion(questionId)
            },
            onSuccess = {
                displayQuestion()
                startTime = System.currentTimeMillis()
            },
            onFailure = { throwable ->
                Log.e(TAG, "Failed to load question $questionId", throwable)
                val errorText = throwable.message ?: throwable.javaClass.simpleName ?: "Unknown error"
                showToast("Error loading question $questionId: $errorText")
            }
        )
    }
    
    private fun displayQuestion() {
        val question = currentQuestion ?: return
        
        binding.textViewQuestionNumber.text = "Question ${currentQuestionIndex + 1} of ${questionIds.size}"
        
        val quizHtml = buildQuestionHtml(question, currentAnswers)
        WebViewRenderer.loadContent(this, binding.webViewQuestion, quizHtml)
        
        binding.textViewTitle.apply {
            if (!question.title.isNullOrBlank()) {
                HtmlUtils.setHtmlText(this, question.title)
            } else {
                text = ""
            }
            isVisible = false
        }

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

        val mediaFiles = collectMediaFiles(question)
        Log.d(TAG, "Question ${question.id} has media files: $mediaFiles")
        mediaHandler.updateMedia(question.id, mediaFiles)
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
        
        answerSubmitted = false
        selectedAnswerId = null
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
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

    private fun buildQuestionHtml(question: Question, answers: List<Answer>): String {
        val questionBody = HtmlUtils.sanitizeForWebView(question.question)
        val explanationSource = question.explanation.ifBlank { "Explanation not provided." }
        val explanation = HtmlUtils.sanitizeForWebView(explanationSource)

        val answersHtml = if (answers.isEmpty()) {
            """
                <p class="empty-state">No answers available for this question.</p>
            """.trimIndent()
        } else {
            answers.mapIndexed { index, answer ->
                val label = ('A'.code + index).toChar()
                val sanitizedAnswer = HtmlUtils.normalizeAnswerHtml(
                    HtmlUtils.sanitizeForWebView(answer.answerText)
                )
                """
                <button type="button"
                        class="answer-button"
                        id="answer-${answer.answerId}"
                        value="${answer.answerId}">
                    <span class="answer-label">$label.</span>
                    <span class="answer-text">$sanitizedAnswer</span>
                </button>
                """.trimIndent()
            }.joinToString("\n")
        }

        val explanationBlock = """
            <section id="explanation" class="explanation hidden">
                <h3>Explanation</h3>
                $explanation
            </section>
        """.trimIndent()

        val scriptBlock = """
            <script>
                (function() {
                    function onReady(callback) {
                        if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', callback);
                        } else {
                            callback();
                        }
                    }

                    function bindAnswerButtons() {
                        var buttons = document.querySelectorAll('.answer-button');
                        buttons.forEach(function(button) {
                            button.addEventListener('click', function() {
                                if (button.classList.contains('locked')) { return; }
                                var answerId = button.value || button.getAttribute('value');
                                if (window.AndroidBridge && answerId) {
                                    window.AndroidBridge.onAnswerSelected(String(answerId));
                                }
                            });
                        });
                    }

                    function setHintVisibility(visible, meta) {
                        var body = document.body;
                        if (!body) { return; }
                        body.classList.toggle('hint-visible', !!visible);
                        if (visible && meta && meta.auto) {
                            body.classList.add('hint-auto');
                        }
                        if (!visible || (meta && meta.manual)) {
                            body.classList.remove('hint-auto');
                        }
                    }

                    function toggleHintVisibility() {
                        var body = document.body;
                        if (!body) { return; }
                        var nextState = !body.classList.contains('hint-visible');
                        setHintVisibility(nextState, { manual: true });
                    }

                    function initializeHintBehavior() {
                        var body = document.body;
                        if (!body) { return; }
                        body.classList.remove('answer-revealed');
                        body.classList.remove('hint-visible', 'hint-auto');
                        var hint = document.getElementById('hintdiv');
                        if (!hint) { return; }
                        setHintVisibility(false, { manual: true });
                        var buttons = document.querySelectorAll('button[onclick]');
                        buttons.forEach(function(button) {
                            var handler = (button.getAttribute('onclick') || '').toLowerCase();
                            if (handler.indexOf('hintdiv') === -1) { return; }
                            button.onclick = function(event) {
                                event.preventDefault();
                                toggleHintVisibility();
                            };
                        });
                    }

                    onReady(function() {
                        bindAnswerButtons();
                        initializeHintBehavior();
                    });

                    window.applyAnswerState = function(correctId, selectedId) {
                        var buttons = document.querySelectorAll('.answer-button');
                        buttons.forEach(function(button) {
                            var id = parseInt(button.value || button.getAttribute('value'));
                            if (isNaN(id)) { return; }
                            button.disabled = true;
                            button.classList.add('locked');
                            button.classList.remove('correct', 'incorrect');
                            if (id === correctId) {
                                button.classList.add('correct');
                            }
                            if (id === selectedId && selectedId !== correctId) {
                                button.classList.add('incorrect');
                            }
                        });
                    };

                    window.setAnswerFeedback = function(text) {
                        var feedback = document.getElementById('answer-feedback');
                        if (feedback) {
                            feedback.textContent = text;
                            feedback.classList.remove('hidden');
                        }
                    };

                    window.revealExplanation = function() {
                        var section = document.getElementById('explanation');
                        if (section) {
                            section.classList.remove('hidden');
                        }
                    };

                    window.markAnswerRevealed = function() {
                        var body = document.body;
                        if (!body) { return; }
                        body.classList.add('answer-revealed');
                        setHintVisibility(true, { auto: true });
                    };
                })();
            </script>
        """.trimIndent()

        return """
            <article class="quiz-block">
                <section class="question-body">$questionBody</section>
                <div id="answer-feedback" class="answer-feedback hidden"></div>
                <section class="answers">$answersHtml</section>
                $explanationBlock
            </article>
            $scriptBlock
        """.trimIndent()
    }

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int) {
        val jsCommand = buildString {
            append("applyAnswerState($correctAnswerId, $selectedAnswerId);")
            append("markAnswerRevealed();")
            append("revealExplanation();")
        }
        binding.webViewQuestion.safeEvaluateJavascript(jsCommand, null)
    }

    private fun configureWebView(webView: WebView) {
        WebViewRenderer.setupWebView(webView)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString() ?: return null
                if (url.startsWith("file://") && url.contains("/media/")) {
                    // Extract filename from URL
                    val fileName = url.substringAfterLast('/')
                    Log.d(TAG, "Intercepting media request for: $fileName")
                    val mediaPath = HtmlUtils.getMediaPath(fileName)
                    if (mediaPath != null) {
                        return try {
                            val file = java.io.File(mediaPath)
                            if (file.exists() && file.canRead()) {
                                val mimeType = getMimeType(fileName)
                                Log.d(TAG, "Serving media file: $fileName with MIME type: $mimeType")
                                WebResourceResponse(mimeType, "UTF-8", file.inputStream())
                            } else {
                                Log.w(TAG, "Media file not found or not readable: $mediaPath")
                                null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load media file: $fileName", e)
                            null
                        }
                    } else {
                        Log.w(TAG, "Could not resolve media path for: $fileName")
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString() ?: return false
                return mediaHandler.handleMediaLink(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return mediaHandler.handleMediaLink(url)
            }

        }
    }

    private inner class QuizWebInterface {
        @JavascriptInterface
        fun onAnswerSelected(answerId: String) {
            val parsedId = answerId.toLongOrNull() ?: return
            runOnUiThread {
                if (answerSubmitted) return@runOnUiThread
                val answer = currentAnswers.firstOrNull { it.answerId == parsedId } ?: return@runOnUiThread
                selectedAnswerId = answer.answerId.toInt()
                submitAnswer()
            }
        }
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
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_filter_subject -> filterDialogHandler.showSubjectFilterDialog(selectedSubjectIds.toSet()) { subjectIds ->
                lifecycleScope.launch {
                    selectedSubjectIds.apply {
                        clear()
                        addAll(subjectIds)
                    }
                    pruneInvalidSystems()
                    reloadQuestionsWithFilters()
                }
            }
            R.id.nav_filter_system -> filterDialogHandler.showSystemFilterDialog(selectedSystemIds.toSet(), selectedSubjectIds.toSet()) { systemIds ->
                selectedSystemIds.apply {
                    clear()
                    addAll(systemIds)
                }
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
        selectedSubjectIds.clear()
        selectedSystemIds.clear()
        if (performanceFilter != PerformanceFilter.ALL) {
            performanceFilter = PerformanceFilter.ALL
            settingsManager.performanceFilterValue = PerformanceFilter.ALL.storageValue
        }
        reloadQuestionsWithFilters()
    }

    private fun showPerformanceFilterDialog() {
        val filters = PerformanceFilter.values()
        val labels = filters.map { getPerformanceFilterLabel(it) }.toTypedArray()
        val currentIndex = filters.indexOf(performanceFilter)

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
        if (performanceFilter == filter) return
        performanceFilter = filter
        settingsManager.performanceFilterValue = filter.storageValue
        reloadQuestionsWithFilters()
    }
    
    private fun reloadQuestionsWithFilters() {
        launchCatching(
            block = { fetchFilteredQuestionIds() },
            onSuccess = { ids ->
                questionIds = ids
                if (questionIds.isEmpty()) {
                    showToast("No questions found with selected filters")
                    binding.textViewStatus.text = "No questions found for current filters"
                    updateToolbarSubtitle()
                } else {
                    binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
                    updateToolbarSubtitle()
                    loadQuestion(0)
                }
            },
            onFailure = { throwable ->
                Log.e(TAG, "Error reloading questions", throwable)
                showToast("Error reloading questions: ${throwable.message}")
            }
        )
    }

    private suspend fun fetchFilteredQuestionIds(): List<Long> {
        val subjectFilter = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
        val systemFilter = selectedSystemIds.takeIf { it.isNotEmpty() }?.toList()
        return databaseManager.getQuestionIds(
            subjectIds = subjectFilter,
            systemIds = systemFilter,
            performanceFilter = performanceFilter
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

    private suspend fun pruneInvalidSystems() {
        if (selectedSystemIds.isEmpty()) return
        val validSystemIds = databaseManager.getSystems(
            selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
        ).map { it.id }.toSet()
        if (validSystemIds.isEmpty()) {
            selectedSystemIds.clear()
        } else {
            selectedSystemIds.retainAll(validSystemIds)
        }
    }

    private fun buildPerformanceSummary(performance: QuestionPerformance?): String {
        val summary = performance?.takeIf { it.attempts > 0 } ?: return ""
        val lastLabel = if (summary.lastCorrect) "Correct" else "Incorrect"
        return "Attempts: ${summary.attempts} | Last: $lastLabel"
    }

    private fun collectMediaFiles(question: Question): List<String> {
        return buildList {
            // Both mediaName and otherMedias can be comma-separated
            addAll(HtmlUtils.parseMediaFiles(question.mediaName))
            addAll(HtmlUtils.parseMediaFiles(question.otherMedias))
        }.distinct()
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
    
    private fun restoreInstanceState(savedInstanceState: Bundle) {
        // Restore quiz state
        currentQuestionIndex = savedInstanceState.getInt(STATE_CURRENT_QUESTION_INDEX, 0)
        questionIds = savedInstanceState.getLongArray(STATE_QUESTION_IDS)?.toList() ?: emptyList()
        selectedSubjectIds.addAll(savedInstanceState.getLongArray(STATE_SELECTED_SUBJECT_IDS)?.toList() ?: emptyList())
        selectedSystemIds.addAll(savedInstanceState.getLongArray(STATE_SELECTED_SYSTEM_IDS)?.toList() ?: emptyList())
        performanceFilter = savedInstanceState.getString(STATE_PERFORMANCE_FILTER)?.let { 
            PerformanceFilter.valueOf(it) 
        } ?: PerformanceFilter.ALL
        testId = savedInstanceState.getString(STATE_TEST_ID) ?: UUID.randomUUID().toString()
        startTime = savedInstanceState.getLong(STATE_START_TIME, System.currentTimeMillis())
        answerSubmitted = savedInstanceState.getBoolean(STATE_ANSWER_SUBMITTED, false)
        selectedAnswerId = if (savedInstanceState.containsKey(STATE_SELECTED_ANSWER_ID)) {
            savedInstanceState.getInt(STATE_SELECTED_ANSWER_ID)
        } else null
        
        val dbPath = intent.getStringExtra(EXTRA_DB_PATH) ?: return
        
        // Initialize database and restore question state
        binding.textViewStatus.text = "Restoring quiz state..."
        
        launchCatching(
            block = {
                databaseManager = MedicalQuizApp.switchDatabase(dbPath)
                mediaHandler.reset()
                filterDialogHandler.updateDatabaseManager(databaseManager)
                
                // Restore current question if available
                val questionId = savedInstanceState.getLong(STATE_CURRENT_QUESTION_ID, -1)
                if (questionId != -1L) {
                    currentQuestion = databaseManager.getQuestionById(questionId)
                    currentAnswers = databaseManager.getAnswersForQuestion(questionId)
                }
            },
            onSuccess = {
                if (questionIds.isEmpty()) {
                    // If no questions were saved, fetch them
                    fetchFilteredQuestionIds()
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

        binding.textViewTitle.isVisible = !binding.textViewTitle.text.isNullOrBlank()
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
            runOnUiThread {
                binding.textViewPerformance.text = ""
            }
            return
        }
        
        launchCatching(
            block = { databaseManager.getQuestionPerformance(questionId) },
            onSuccess = { performance ->
                currentPerformance = performance
                runOnUiThread {
                    binding.textViewPerformance.text = buildPerformanceSummary(performance)
                }
            },
            onFailure = { throwable ->
                currentPerformance = null
                Log.w(TAG, "Unable to load performance for question $questionId", throwable)
                runOnUiThread {
                    binding.textViewPerformance.text = ""
                }
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
