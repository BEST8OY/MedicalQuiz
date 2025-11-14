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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        
        val dbPath = intent.getStringExtra("DB_PATH")
        val dbName = intent.getStringExtra("DB_NAME")
        
        if (dbPath == null) {
            Toast.makeText(this, "No database selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = dbName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        databaseManager = DatabaseManager(dbPath)
        mediaHandler = MediaHandler(this, lifecycleScope, databaseManager) { questionIds.getOrNull(currentQuestionIndex) }
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
        
        lifecycleScope.launch {
            try {
                // Use global database manager to ensure proper cleanup
                databaseManager = MedicalQuizApp.switchDatabase(dbPath)
                mediaHandler.updateDatabaseManager(databaseManager)
                filterDialogHandler.updateDatabaseManager(databaseManager)
                questionIds = fetchFilteredQuestionIds()

                if (questionIds.isEmpty()) {
                    binding.textViewStatus.text = "No questions found for current filters"
                    updateToolbarSubtitle()
                    return@launch
                }

                binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
                updateToolbarSubtitle()
                loadQuestion(0)
            } catch (e: Exception) {
                binding.textViewStatus.text = "Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }
    
    private fun loadQuestion(index: Int) {
        if (index < 0 || index >= questionIds.size) return
        
        currentQuestionIndex = index
        val questionId = questionIds[index]
        
        lifecycleScope.launch {
            try {
            currentPerformance = null
                currentQuestion = databaseManager.getQuestionById(questionId)
                currentAnswers = databaseManager.getAnswersForQuestion(questionId)
                currentPerformance = databaseManager.getQuestionPerformance(questionId)

                displayQuestion()
                startTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error loading question: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun displayQuestion() {
        val question = currentQuestion ?: return
        
        // Update question counter
        binding.textViewQuestionNumber.text = "Question ${currentQuestionIndex + 1} of ${questionIds.size}"
        
        // Render question, answers, and explanation within a single WebView
        val quizHtml = buildQuestionHtml(question, currentAnswers)
        WebViewRenderer.loadContent(this, binding.webViewQuestion, quizHtml)
        
        // Display title if available
        if (!question.title.isNullOrBlank()) {
            HtmlUtils.setHtmlText(binding.textViewTitle, question.title)
        } else {
            binding.textViewTitle.text = ""
        }
        binding.textViewTitle.visibility = View.GONE
        
        // Display subject and system
        val metadata = buildString {
            append("ID: ${question.id}")
            if (!question.subName.isNullOrBlank()) {
                append(" | Subject: ${question.subName}")
            }
            if (!question.sysName.isNullOrBlank()) {
                append(" | System: ${question.sysName}")
            }
        }
        binding.textViewMetadata.text = metadata
        binding.textViewMetadata.visibility = View.GONE

        val performanceText = buildPerformanceSummary(currentPerformance)
        binding.textViewPerformance.text = performanceText
        binding.textViewPerformance.visibility = View.GONE
        
        // Display media info if available
        if (!question.mediaName.isNullOrBlank() || !question.otherMedias.isNullOrBlank()) {
            val mediaFiles = mutableListOf<String>()
            question.mediaName?.let { mediaFiles.add(it) }
            HtmlUtils.parseMediaFiles(question.otherMedias).let { mediaFiles.addAll(it) }
            binding.textViewMediaInfo.text = "📎 ${mediaFiles.size} media file(s) - Click to view"
            binding.textViewMediaInfo.visibility = View.GONE
            
            // Make media info clickable
            binding.textViewMediaInfo.setOnClickListener {
                mediaHandler.openMediaViewer(mediaFiles, 0)
            }
        } else {
            binding.textViewMediaInfo.visibility = View.GONE
            binding.textViewMediaInfo.setOnClickListener(null)
        }
        
        // Reset UI state
        answerSubmitted = false
        selectedAnswerId = null
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
    }
    
    private fun submitAnswer() {
        val answerId = selectedAnswerId
        if (answerId == null) {
            Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (answerSubmitted) return
        answerSubmitted = true
        
        val question = currentQuestion ?: return
        val timeTaken = System.currentTimeMillis() - startTime
        
        lifecycleScope.launch {
            try {
                // Log the answer
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
                val correctAnswerId = correctAnswer?.answerId?.toInt()
                val correctAnswerText = correctAnswer?.let { HtmlUtils.stripHtml(it.answerText) } ?: "Answer ${question.corrAns}"
                val normalizedCorrectId = correctAnswerId ?: -1
                val wasCorrect = normalizedCorrectId == answerId
                updateLocalPerformanceCache(question.id, wasCorrect)
                updateWebViewAnswerState(normalizedCorrectId, answerId, correctAnswerText)
                showQuestionDetails()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error saving answer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                val sanitizedAnswer = HtmlUtils.sanitizeForWebView(answer.answerText)
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

    private fun updateWebViewAnswerState(correctAnswerId: Int, selectedAnswerId: Int, correctAnswerText: String) {
        val jsCommand = buildString {
            append("applyAnswerState($correctAnswerId, $selectedAnswerId);")
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
        if (currentQuestionIndex < questionIds.size - 1) {
            loadQuestion(currentQuestionIndex + 1)
        }
    }
    
    private fun loadPreviousQuestion() {
        if (currentQuestionIndex > 0) {
            loadQuestion(currentQuestionIndex - 1)
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
            R.id.nav_about -> Toast.makeText(this, "About coming soon", Toast.LENGTH_SHORT).show()
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
        lifecycleScope.launch {
            try {
                questionIds = fetchFilteredQuestionIds()
                
                if (questionIds.isEmpty()) {
                    Toast.makeText(this@QuizActivity, "No questions found with selected filters", Toast.LENGTH_SHORT).show()
                    binding.textViewStatus.text = "No questions found for current filters"
                    updateToolbarSubtitle()
                    return@launch
                }

                binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
                updateToolbarSubtitle()
                loadQuestion(0)
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error reloading questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchFilteredQuestionIds(): List<Long> {
        val subjectFilter = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
        val systemFilter = selectedSystemIds.takeIf { it.isNotEmpty() }?.toList()
        val performanceIds = if (performanceFilter == PerformanceFilter.ALL) {
            null
        } else {
            databaseManager.getPerformanceFilteredIds(performanceFilter)
        }
        return databaseManager.getQuestionIds(
            subjectIds = subjectFilter,
            systemIds = systemFilter,
            performanceIds = performanceIds
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
        val summary = performance ?: return ""
        if (summary.attempts <= 0) return ""
        val lastLabel = if (summary.lastCorrect) "Correct" else "Incorrect"
        return "Attempts: ${summary.attempts} | Last: $lastLabel"
    }

    private fun updateLocalPerformanceCache(questionId: Long, wasCorrect: Boolean) {
        val existing = currentPerformance
        currentPerformance = if (existing == null) {
            QuestionPerformance(
                qid = questionId,
                lastCorrect = wasCorrect,
                everCorrect = wasCorrect,
                everIncorrect = !wasCorrect,
                attempts = 1
            )
        } else {
            existing.copy(
                lastCorrect = wasCorrect,
                everCorrect = existing.everCorrect || wasCorrect,
                everIncorrect = existing.everIncorrect || !wasCorrect,
                attempts = existing.attempts + 1
            )
        }
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
        val hasTitle = !binding.textViewTitle.text.isNullOrBlank()
        binding.textViewTitle.visibility = if (hasTitle) View.VISIBLE else View.GONE

        val hasMetadata = !binding.textViewMetadata.text.isNullOrBlank()
        binding.textViewMetadata.visibility = if (hasMetadata) View.VISIBLE else View.GONE

        val hasPerformance = !binding.textViewPerformance.text.isNullOrBlank()
        binding.textViewPerformance.visibility = if (hasPerformance) View.VISIBLE else View.GONE

        val hasMediaInfo = !binding.textViewMediaInfo.text.isNullOrBlank()
        binding.textViewMediaInfo.visibility = if (hasMediaInfo) View.VISIBLE else View.GONE
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
                            Toast.makeText(this@QuizActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@QuizActivity, "Failed to clear logs: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

}
