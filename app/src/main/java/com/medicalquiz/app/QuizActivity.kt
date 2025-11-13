package com.medicalquiz.app

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.utils.HtmlUtils
import kotlinx.coroutines.launch
import java.util.UUID

class QuizActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityQuizBinding
    private lateinit var databaseManager: DatabaseManager
    private lateinit var drawerToggle: ActionBarDrawerToggle
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
        
        setupDrawer()
        setupListeners()
        initializeDatabase()
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
        binding.radioGroupAnswers.setOnCheckedChangeListener { _, checkedId ->
            if (answerSubmitted) return@setOnCheckedChangeListener
            
            when (checkedId) {
                binding.radioAnswer1.id -> selectedAnswerId = 1
                binding.radioAnswer2.id -> selectedAnswerId = 2
                binding.radioAnswer3.id -> selectedAnswerId = 3
                binding.radioAnswer4.id -> selectedAnswerId = 4
                binding.radioAnswer5.id -> selectedAnswerId = 5
            }
            
            // Auto-submit when answer is selected
            selectedAnswerId?.let { submitAnswer() }
        }
        
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
        
        // Display question text with HTML support
        HtmlUtils.setHtmlText(binding.textViewQuestion, question.question)
        
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
            binding.textViewMediaInfo.text = "📎 ${mediaFiles.size} media file(s)"
            binding.textViewMediaInfo.visibility = android.view.View.VISIBLE
        } else {
            binding.textViewMediaInfo.visibility = android.view.View.GONE
        }
        
        // Display answers
        displayAnswers()
        
        // Reset UI state
        answerSubmitted = false
        selectedAnswerId = null
        binding.radioGroupAnswers.clearCheck()
        binding.textViewExplanation.visibility = android.view.View.GONE
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
        
        // Reset answer colors
        resetAnswerColors()
        
        // Hide explanation initially
        binding.textViewExplanation.text = ""
    }
    
    private fun resetAnswerColors() {
        val radioButtons = listOf(
            binding.radioAnswer1,
            binding.radioAnswer2,
            binding.radioAnswer3,
            binding.radioAnswer4,
            binding.radioAnswer5
        )
        radioButtons.forEach { btn ->
            btn.setTextColor(Color.BLACK)
            btn.setBackgroundColor(Color.TRANSPARENT)
        }
    }
    
    private fun displayAnswers() {
        val answers = currentAnswers
        
        // Show/hide radio buttons based on available answers
        val radioButtons = listOf(
            binding.radioAnswer1,
            binding.radioAnswer2,
            binding.radioAnswer3,
            binding.radioAnswer4,
            binding.radioAnswer5
        )
        
        answers.forEachIndexed { index, answer ->
            if (index < radioButtons.size) {
                // Render HTML inside RadioButton without enabling link clicks
                HtmlUtils.setHtmlText(radioButtons[index], answer.answerText, enableLinks = false)
                radioButtons[index].visibility = android.view.View.VISIBLE
                radioButtons[index].isEnabled = true
            }
        }
        
        // Hide unused radio buttons
        for (i in answers.size until radioButtons.size) {
            radioButtons[i].visibility = android.view.View.GONE
        }
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
                highlightAnswers(answerId, question.corrAns)
                
                // Get correct answer text
                val correctAnswer = currentAnswers.getOrNull(question.corrAns - 1)
                val correctAnswerText = correctAnswer?.let { HtmlUtils.stripHtml(it.answerText) } ?: "Answer ${question.corrAns}"
                
                // Show explanation without Correct/Incorrect text
                val explanationHtml = "<strong>Correct Answer: $correctAnswerText</strong><br><br>${question.explanation}"
                
                HtmlUtils.setHtmlText(binding.textViewExplanation, explanationHtml)
                binding.textViewExplanation.visibility = android.view.View.VISIBLE
                
                // Disable all radio buttons
                disableAllAnswers()
                
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error saving answer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun highlightAnswers(selectedId: Int, correctId: Int) {
        val radioButtons = listOf(
            binding.radioAnswer1,
            binding.radioAnswer2,
            binding.radioAnswer3,
            binding.radioAnswer4,
            binding.radioAnswer5
        )
        
        radioButtons.forEachIndexed { index, radioButton ->
            val answerId = index + 1
            when {
                answerId == correctId -> {
                    // Highlight correct answer in green
                    radioButton.setTextColor(Color.parseColor("#1B5E20"))
                    radioButton.setBackgroundColor(Color.parseColor("#C8E6C9"))
                }
                answerId == selectedId && selectedId != correctId -> {
                    // Highlight wrong answer in red
                    radioButton.setTextColor(Color.parseColor("#B71C1C"))
                    radioButton.setBackgroundColor(Color.parseColor("#FFCDD2"))
                }
            }
        }
    }
    
    private fun disableAllAnswers() {
        val radioButtons = listOf(
            binding.radioAnswer1,
            binding.radioAnswer2,
            binding.radioAnswer3,
            binding.radioAnswer4,
            binding.radioAnswer5
        )
        radioButtons.forEach { it.isEnabled = false }
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
            R.id.nav_filter_subject -> showSubjectFilterDialog()
            R.id.nav_filter_system -> showSystemFilterDialog()
            R.id.nav_clear_filters -> clearFilters()
            R.id.nav_settings -> Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
            R.id.nav_about -> Toast.makeText(this, "About coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showSubjectFilterDialog() {
        lifecycleScope.launch {
            try {
                val subjects = databaseManager.getSubjects()
                val subjectNames = subjects.map { it.name }.toTypedArray()
                
                AlertDialog.Builder(this@QuizActivity)
                    .setTitle("Filter by Subject")
                    .setItems(subjectNames) { _, which ->
                        selectedSubjectId = subjects[which].id
                        reloadQuestionsWithFilters()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error loading subjects: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showSystemFilterDialog() {
        lifecycleScope.launch {
            try {
                val systemIds = selectedSubjectId?.let { listOf(it) }
                val systems = databaseManager.getSystems(systemIds)
                val systemNames = systems.map { it.name }.toTypedArray()
                
                AlertDialog.Builder(this@QuizActivity)
                    .setTitle("Filter by System")
                    .setItems(systemNames) { _, which ->
                        selectedSystemId = systems[which].id
                        reloadQuestionsWithFilters()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error loading systems: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
                    subjectId = selectedSubjectId,
                    systemId = selectedSystemId
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
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
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
