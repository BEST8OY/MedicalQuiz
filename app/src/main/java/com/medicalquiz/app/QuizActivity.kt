package com.medicalquiz.app

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System as QuizSystem
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
import com.medicalquiz.app.databinding.LayoutFilterDrawerBinding
import com.medicalquiz.app.utils.HtmlUtils
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.util.UUID

class QuizActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuizBinding
    private lateinit var databaseManager: DatabaseManager
    private var questionIds: List<Long> = emptyList()
    private var currentQuestionIndex = 0
    private var currentQuestion: Question? = null
    private var currentAnswers: List<Answer> = emptyList()
    private var selectedAnswerId: Int? = null
    private val testId = UUID.randomUUID().toString()
    private var startTime: Long = 0
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var filterBinding: LayoutFilterDrawerBinding
    private val selectedSubjectIds = mutableSetOf<Long>()
    private val selectedSystemIds = mutableSetOf<Long>()
    private var allSubjects: List<Subject> = emptyList()
    private var currentSystems: List<QuizSystem> = emptyList()
    private var isAnswerRevealed = false
    private var suppressAnswerChange = false
    private var defaultAnswerTextColor: Int = Color.BLACK
    private var neutralAnswerBackground: Int = Color.TRANSPARENT
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        filterBinding = LayoutFilterDrawerBinding.bind(binding.navigationFilters.getHeaderView(0))

        val dbPath = intent.getStringExtra("DB_PATH")
        val dbName = intent.getStringExtra("DB_NAME")
        
        if (dbPath == null) {
            Toast.makeText(this, "No database selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar(dbName)
        defaultAnswerTextColor = binding.radioAnswer1.currentTextColor
        neutralAnswerBackground = ContextCompat.getColor(this, R.color.answer_neutral_bg)
        
        databaseManager = DatabaseManager(dbPath)
        
        setupListeners()
        setupFilterDrawer()
        initializeDatabase()
    }
    
    private fun setupListeners() {
        binding.radioGroupAnswers.setOnCheckedChangeListener { _, checkedId ->
            if (suppressAnswerChange || checkedId == -1 || isAnswerRevealed) return@setOnCheckedChangeListener
            val answerNumber = when (checkedId) {
                binding.radioAnswer1.id -> 1
                binding.radioAnswer2.id -> 2
                binding.radioAnswer3.id -> 3
                binding.radioAnswer4.id -> 4
                binding.radioAnswer5.id -> 5
                else -> null
            }
            answerNumber?.let {
                selectedAnswerId = it
                submitAnswer()
            }
        }
        
        binding.buttonNext.setOnClickListener {
            loadNextQuestion()
        }
        
        binding.buttonPrevious.setOnClickListener {
            loadPreviousQuestion()
        }
    }

    private fun setupToolbar(dbName: String?) {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = dbName ?: getString(R.string.app_name)
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }

    private fun setupFilterDrawer() {
        filterBinding.buttonApplyFilters.setOnClickListener {
            applyFilters()
        }
        filterBinding.buttonClearFilters.setOnClickListener {
            clearFilters()
        }
    }

    private suspend fun loadFilterOptions() {
        allSubjects = databaseManager.getSubjects()
        populateSubjectChips(allSubjects)
        currentSystems = databaseManager.getSystems()
        populateSystemChips(currentSystems)
    }

    private fun populateSubjectChips(subjects: List<Subject>) {
        filterBinding.chipGroupSubjects.removeAllViews()
        subjects.forEach { subject ->
            val chip = createFilterChip(subject.id, "${subject.name} (${subject.count})")
            chip.isChecked = selectedSubjectIds.contains(subject.id)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSubjectIds.add(subject.id)
                } else {
                    selectedSubjectIds.remove(subject.id)
                }
                refreshSystemsForSelection()
            }
            filterBinding.chipGroupSubjects.addView(chip)
        }
    }

    private fun populateSystemChips(systems: List<QuizSystem>) {
        filterBinding.chipGroupSystems.removeAllViews()
        val availableIds = systems.map { it.id }.toSet()
        selectedSystemIds.retainAll(availableIds)
        systems.forEach { system ->
            val chip = createFilterChip(system.id, "${system.name} (${system.count})")
            chip.isChecked = selectedSystemIds.contains(system.id)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSystemIds.add(system.id)
                } else {
                    selectedSystemIds.remove(system.id)
                }
            }
            filterBinding.chipGroupSystems.addView(chip)
        }
    }

    private fun createFilterChip(id: Long, label: String): Chip {
        return Chip(this).apply {
            text = label
            tag = id
            isCheckable = true
            isClickable = true
            isCheckedIconVisible = true
        }
    }

    private fun refreshSystemsForSelection() {
        lifecycleScope.launch {
            try {
                val subjects = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                currentSystems = databaseManager.getSystems(subjects)
                populateSystemChips(currentSystems)
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error loading systems: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilters() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        lifecycleScope.launch {
            try {
                binding.textViewStatus.text = getString(R.string.loading_databases)
                binding.textViewStatus.visibility = View.VISIBLE
                val subjects = selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                val systems = selectedSystemIds.takeIf { it.isNotEmpty() }?.toList()
                questionIds = databaseManager.getQuestionIds(subjects, systems)
                if (questionIds.isEmpty()) {
                    binding.textViewStatus.text = getString(R.string.no_questions_for_filters)
                    binding.textViewStatus.visibility = View.VISIBLE
                    currentQuestion = null
                    currentAnswers = emptyList()
                    binding.textViewQuestionNumber.text = ""
                    binding.textViewQuestion.text = ""
                    resetAnswerViews()
                    suppressAnswerChange = true
                    binding.radioGroupAnswers.clearCheck()
                    suppressAnswerChange = false
                    binding.textViewExplanation.visibility = View.GONE
                    binding.buttonNext.isEnabled = false
                    binding.buttonPrevious.isEnabled = false
                } else {
                    binding.textViewStatus.text = "Filtered ${questionIds.size} question(s)"
                    loadQuestion(0)
                }
            } catch (e: Exception) {
                binding.textViewStatus.text = e.message
            }
        }
    }

    private fun clearFilters() {
        selectedSubjectIds.clear()
        selectedSystemIds.clear()
        lifecycleScope.launch {
            try {
                populateSubjectChips(allSubjects)
                currentSystems = databaseManager.getSystems()
                populateSystemChips(currentSystems)
                applyFilters()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error clearing filters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun initializeDatabase() {
        binding.textViewStatus.text = "Loading database..."
        binding.textViewStatus.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Use global database manager to ensure proper cleanup
                databaseManager = MedicalQuizApp.switchDatabase(intent.getStringExtra("DB_PATH")!!)
                questionIds = databaseManager.getQuestionIds()
                loadFilterOptions()
                
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
        binding.textViewStatus.visibility = View.GONE
        
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
        selectedAnswerId = null
        resetAnswerViews()
        suppressAnswerChange = true
        binding.radioGroupAnswers.clearCheck()
        suppressAnswerChange = false
        binding.textViewExplanation.visibility = android.view.View.GONE
        binding.textViewExplanation.text = ""
        isAnswerRevealed = false
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
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

    private fun getAnswerButtons(): List<RadioButton> = listOf(
        binding.radioAnswer1,
        binding.radioAnswer2,
        binding.radioAnswer3,
        binding.radioAnswer4,
        binding.radioAnswer5
    )

    private fun resetAnswerViews() {
        val defaultColor = defaultAnswerTextColor
        val neutralBg = neutralAnswerBackground
        getAnswerButtons().forEach { button ->
            button.setBackgroundColor(neutralBg)
            button.setTextColor(defaultColor)
            button.isEnabled = true
        }
    }

    private fun highlightAnswers(selectedAnswer: Int, correctAnswer: Int) {
        val correctBg = ContextCompat.getColor(this, R.color.answer_correct_bg)
        val correctText = ContextCompat.getColor(this, R.color.answer_correct_text)
        val incorrectBg = ContextCompat.getColor(this, R.color.answer_incorrect_bg)
        val incorrectText = ContextCompat.getColor(this, R.color.answer_incorrect_text)

        getAnswerButtons().forEachIndexed { index, button ->
            val answerNumber = index + 1
            button.isEnabled = false
            when {
                answerNumber == correctAnswer -> {
                    button.setBackgroundColor(correctBg)
                    button.setTextColor(correctText)
                }
                answerNumber == selectedAnswer && selectedAnswer != correctAnswer -> {
                    button.setBackgroundColor(incorrectBg)
                    button.setTextColor(incorrectText)
                }
                else -> {
                    button.setBackgroundColor(neutralAnswerBackground)
                    button.setTextColor(defaultAnswerTextColor)
                }
            }
        }
    }
    
    private fun submitAnswer() {
        val answerId = selectedAnswerId
        if (answerId == null) {
            Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show()
            return
        }
        
        val question = currentQuestion ?: return
        if (isAnswerRevealed) return
        val timeTaken = System.currentTimeMillis() - startTime
        isAnswerRevealed = true
        highlightAnswers(answerId, question.corrAns)
        val explanationHtml = "<strong>Answer ${question.corrAns}</strong><br><br>${question.explanation}".trim()
        HtmlUtils.setHtmlText(binding.textViewExplanation, explanationHtml)
        binding.textViewExplanation.visibility = View.VISIBLE
        
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
                
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Error saving answer: ${e.message}", Toast.LENGTH_SHORT).show()
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
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        if (!handlePendingLogsBeforeExit()) {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (handlePendingLogsBeforeExit()) true else super.onSupportNavigateUp()
    }

    private fun handlePendingLogsBeforeExit(): Boolean {
        if (!::databaseManager.isInitialized) {
            finish()
            return true
        }
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
