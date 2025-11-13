package com.medicalquiz.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.ui.AnswerHandler
import com.medicalquiz.app.ui.FilterDialogHandler
import com.medicalquiz.app.ui.MediaHandler
import com.medicalquiz.app.utils.HtmlUtils
import com.medicalquiz.app.utils.WebViewRenderer
import kotlinx.coroutines.launch
import java.util.UUID

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityQuizBinding
    private lateinit var databaseManager: DatabaseManager
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var mediaHandler: MediaHandler
    private lateinit var answerHandler: AnswerHandler
    private lateinit var filterDialogHandler: FilterDialogHandler
    
    private var questionIds: List<Long> = emptyList()
    private var currentQuestionIndex = 0
    private var currentQuestion: Question? = null
    private var currentAnswers: List<Answer> = emptyList()
    private var selectedAnswerId: Int? = null
    private val testId = UUID.randomUUID().toString()
    private var startTime: Long = 0
    private var selectedSubjectId: Long? = null
    private var selectedSystemId: Long? = null
    private var answerSubmitted = false
    private val answerCheckedChangeListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
        if (answerSubmitted) return@OnCheckedChangeListener
        val radioButton = group.findViewById<RadioButton>(checkedId) ?: return@OnCheckedChangeListener
        val index = group.indexOfChild(radioButton)
        val answer = currentAnswers.getOrNull(index) ?: return@OnCheckedChangeListener
        selectedAnswerId = answer.answerId.toInt()
        submitAnswer()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        answerHandler = AnswerHandler()
        filterDialogHandler = FilterDialogHandler(this, lifecycleScope, databaseManager)
        
        setupWebViews()
        setupDrawer()
        setupListeners()
        initializeDatabase()
        
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
        WebViewRenderer.setupWebView(binding.webViewQuestion)
        WebViewRenderer.setupWebView(binding.webViewExplanation)
        
        // Setup click handler for images in WebViews
        mediaHandler.setupWebViewImageClicks(binding.webViewQuestion)
        mediaHandler.setupWebViewImageClicks(binding.webViewExplanation)
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
        binding.radioGroupAnswers.setOnCheckedChangeListener(answerCheckedChangeListener)
        
        binding.buttonNext.setOnClickListener {
            loadNextQuestion()
        }
        
        binding.buttonPrevious.setOnClickListener {
            loadPreviousQuestion()
        }
    }
    
    private fun initializeDatabase() {
        binding.textViewStatus.text = "Loading database..."
        
        lifecycleScope.launch {
            try {
                // Use global database manager to ensure proper cleanup
                databaseManager = MedicalQuizApp.switchDatabase(intent.getStringExtra("DB_PATH")!!)
                questionIds = databaseManager.getQuestionIds()
                
                if (questionIds.isEmpty()) {
                    binding.textViewStatus.text = "No questions found in database"
                    return@launch
                }
                
                binding.textViewStatus.text = "Loaded ${questionIds.size} questions"
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
                currentQuestion = databaseManager.getQuestionById(questionId)
                currentAnswers = databaseManager.getAnswersForQuestion(questionId)
                
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
        
        // Display question text with HTML support via WebView
        WebViewRenderer.loadContent(this, binding.webViewQuestion, question.question)
        
        // Display title if available
        if (!question.title.isNullOrBlank()) {
            HtmlUtils.setHtmlText(binding.textViewTitle, question.title)
            binding.textViewTitle.visibility = android.view.View.VISIBLE
        } else {
            binding.textViewTitle.visibility = android.view.View.GONE
        }
        
        // Display subject and system
        val metadata = buildString {
            if (!question.subName.isNullOrBlank()) append("Subject: ${question.subName}")
            if (!question.sysName.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append("System: ${question.sysName}")
            }
        }
        binding.textViewMetadata.text = metadata
        
        // Display media info if available
        if (!question.mediaName.isNullOrBlank() || !question.otherMedias.isNullOrBlank()) {
            val mediaFiles = mutableListOf<String>()
            question.mediaName?.let { mediaFiles.add(it) }
            HtmlUtils.parseMediaFiles(question.otherMedias).let { mediaFiles.addAll(it) }
            binding.textViewMediaInfo.text = "📎 ${mediaFiles.size} media file(s) - Click to view"
            binding.textViewMediaInfo.visibility = android.view.View.VISIBLE
            
            // Make media info clickable
            binding.textViewMediaInfo.setOnClickListener {
                mediaHandler.openMediaViewer(mediaFiles, 0)
            }
        } else {
            binding.textViewMediaInfo.visibility = android.view.View.GONE
            binding.textViewMediaInfo.setOnClickListener(null)
        }
        
        // Display answers
        displayAnswers()
        
        // Reset UI state
        answerSubmitted = false
        selectedAnswerId = null
        binding.radioGroupAnswers.clearCheck()
        binding.webViewExplanation.visibility = android.view.View.GONE
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
        
        // Reset answer colors
        answerHandler.resetAnswerColors(binding.radioGroupAnswers)
        
        // Hide explanation initially
        binding.webViewExplanation.loadData("", "text/html", "UTF-8")
    }
    
    private fun displayAnswers() {
        val radioGroup = binding.radioGroupAnswers
        radioGroup.setOnCheckedChangeListener(null)
        radioGroup.removeAllViews()
        
        currentAnswers.forEach { answer ->
            val radioButton = createAnswerRadioButton()
            HtmlUtils.setHtmlText(radioButton, answer.answerText, enableLinks = false)
            radioButton.id = View.generateViewId()
            radioButton.tag = answer.answerId
            radioGroup.addView(radioButton)
        }
        
        radioGroup.clearCheck()
        radioGroup.setOnCheckedChangeListener(answerCheckedChangeListener)
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
                databaseManager.logAnswer(
                    qid = question.id,
                    selectedAnswer = answerId,
                    corrAnswer = question.corrAns,
                    time = timeTaken,
                    testId = testId
                )
                
                // Highlight answers
                answerHandler.highlightAnswers(
                    binding.radioGroupAnswers,
                    currentAnswers,
                    answerId,
                    question.corrAns
                )
                
                // Get correct answer text
                val correctAnswer = currentAnswers.getOrNull(question.corrAns - 1)
                val correctAnswerText = correctAnswer?.let { HtmlUtils.stripHtml(it.answerText) } ?: "Answer ${question.corrAns}"
                
                // Show explanation without Correct/Incorrect text
                val explanationHtml = "<strong>Correct Answer: $correctAnswerText</strong><br><br>${question.explanation}"
                
                WebViewRenderer.loadContent(this@QuizActivity, binding.webViewExplanation, explanationHtml)
                binding.webViewExplanation.visibility = android.view.View.VISIBLE
                
                // Disable all radio buttons
                disableAllAnswers()
                
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error saving answer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun disableAllAnswers() {
        for (i in 0 until binding.radioGroupAnswers.childCount) {
            binding.radioGroupAnswers.getChildAt(i)?.isEnabled = false
        }
    }

    private fun createAnswerRadioButton(): MaterialRadioButton {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layoutParams = RadioGroup.LayoutParams(
            RadioGroup.LayoutParams.MATCH_PARENT,
            RadioGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val spacing = (8 * resources.displayMetrics.density).toInt()
            topMargin = spacing
            bottomMargin = spacing
        }
        return MaterialRadioButton(this).apply {
            this.layoutParams = layoutParams
            setPadding(padding, padding, padding, padding)
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
            R.id.nav_filter_subject -> filterDialogHandler.showSubjectFilterDialog(selectedSubjectId) { subjectId ->
                selectedSubjectId = subjectId
                reloadQuestionsWithFilters()
            }
            R.id.nav_filter_system -> filterDialogHandler.showSystemFilterDialog(selectedSystemId, selectedSubjectId) { systemId ->
                selectedSystemId = systemId
                reloadQuestionsWithFilters()
            }
            R.id.nav_clear_filters -> clearFilters()
            R.id.nav_settings -> Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
            R.id.nav_about -> Toast.makeText(this, "About coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun clearFilters() {
        selectedSubjectId = null
        selectedSystemId = null
        reloadQuestionsWithFilters()
    }
    
    private fun reloadQuestionsWithFilters() {
        lifecycleScope.launch {
            try {
                questionIds = databaseManager.getQuestionIds(
                    subjectIds = selectedSubjectId?.let { listOf(it) },
                    systemIds = selectedSystemId?.let { listOf(it) }
                )
                
                if (questionIds.isEmpty()) {
                    Toast.makeText(this@QuizActivity, "No questions found with selected filters", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                updateToolbarSubtitle()
                loadQuestion(0)
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error reloading questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateToolbarSubtitle() {
        lifecycleScope.launch {
            val subtitle = buildString {
                selectedSubjectId?.let { subId ->
                    val subject = databaseManager.getSubjects().find { it.id == subId }
                    subject?.let { append("Subject: ${it.name}") }
                }
                selectedSystemId?.let { sysId ->
                    val systems = databaseManager.getSystems()
                    val system = systems.find { it.id == sysId }
                    system?.let {
                        if (isNotEmpty()) append(" | ")
                        append("System: ${it.name}")
                    }
                }
            }
            supportActionBar?.subtitle = subtitle.ifEmpty { null }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        // Check if there are pending logs
        val pendingLogs = databaseManager.getPendingLogCount()
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
}
