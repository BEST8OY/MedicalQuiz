package com.medicalquiz.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import com.medicalquiz.app.settings.SettingsManager
import kotlinx.coroutines.launch
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
        initializeDatabase(dbPath)
        
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
        loadPerformanceForQuestion(question.id)

        val mediaFiles = collectMediaFiles(question)
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
                }

                val correctAnswer = currentAnswers.getOrNull(question.corrAns - 1)
                val normalizedCorrectId = correctAnswer?.answerId?.toInt() ?: -1
                val wasCorrect = normalizedCorrectId == answerId
                updateLocalPerformanceCache(question.id, wasCorrect)
                updateWebViewAnswerState(normalizedCorrectId, answerId)
                showQuestionDetails()
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
                    val sanitizedAnswer = sanitizeAnswerHtml(HtmlUtils.sanitizeForWebView(answer.answerText))
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
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', bindAnswerButtons);
                    } else {
                        bindAnswerButtons();
                    }
                })();

                function applyAnswerState(correctId, selectedId) {
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
                }

                function setAnswerFeedback(text) {
                    var feedback = document.getElementById('answer-feedback');
                    if (feedback) {
                        feedback.textContent = text;
                        feedback.classList.remove('hidden');
                    }
                }

                function revealExplanation() {
                    var section = document.getElementById('explanation');
                    if (section) {
                        section.classList.remove('hidden');
                    }
                }

                function markAnswersRevealed() {
                    if (document && document.body) {
                        document.body.classList.add('answers-revealed');
                    }
                }

                function revealHintSections() {
                    var hints = document.querySelectorAll('#hintdiv');
                    hints.forEach(function(hint) {
                        hint.style.display = 'block';
                    });
                }

                function handlePostAnswerState() {
                    markAnswersRevealed();
                    revealHintSections();
                }
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
            append("handlePostAnswerState();")
            append("revealExplanation();")
        }
        binding.webViewQuestion.evaluateJavascript(jsCommand, null)
    }

    private fun configureWebView(webView: WebView) {
        WebViewRenderer.setupWebView(webView)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
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
            question.mediaName?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
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
        binding.textViewPerformance.text = buildPerformanceSummary(currentPerformance)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        // Check if there are pending logs
        val pendingLogs = if (settingsManager.isLoggingEnabled) databaseManager.getPendingLogCount() else 0
        if (pendingLogs > 0) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Answers")
                .setMessage("You have $pendingLogs unsaved answer(s). Save before leaving?")
                .setPositiveButton("Save & Exit") { _, _ ->
                    lifecycleScope.launch {
                        databaseManager.flushLogs()
                        finish()
                    }
                }
                .setNegativeButton("Exit Without Saving") { _, _ ->
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            finish()
        }
        return true
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
        binding.textViewPerformance.isVisible = !binding.textViewPerformance.text.isNullOrBlank()
        binding.textViewMediaInfo.isVisible = !binding.textViewMediaInfo.text.isNullOrBlank()

        binding.scrollViewContent.post {
            binding.scrollViewContent.scrollTo(0, previousScrollY)
        }
    }

    private fun loadPerformanceForQuestion(questionId: Long) {
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun sanitizeAnswerHtml(answerHtml: String): String {
        if (answerHtml.isBlank()) return answerHtml
        var sanitized = answerHtml.trim()
        sanitized = PARAGRAPH_BREAK_REGEX.replace(sanitized) { "<br><br>" }
        sanitized = PARAGRAPH_TAG_REGEX.replace(sanitized, "")
        return sanitized.trim()
    }

    companion object {
        private const val TAG = "QuizActivity"
        private const val EXTRA_DB_PATH = "DB_PATH"
        private const val EXTRA_DB_NAME = "DB_NAME"
        private val PARAGRAPH_BREAK_REGEX = Regex("(?i)</p>\\s*<p>")
        private val PARAGRAPH_TAG_REGEX = Regex("(?i)</?p>")
    }
}
