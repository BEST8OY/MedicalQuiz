package com.medicalquiz.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.databinding.ActivityQuizBinding
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
        
        supportActionBar?.title = dbName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        databaseManager = DatabaseManager(dbPath)
        
        setupListeners()
        initializeDatabase()
    }
    
    private fun setupListeners() {
        binding.radioGroupAnswers.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioAnswer1.id -> selectedAnswerId = 1
                binding.radioAnswer2.id -> selectedAnswerId = 2
                binding.radioAnswer3.id -> selectedAnswerId = 3
                binding.radioAnswer4.id -> selectedAnswerId = 4
                binding.radioAnswer5.id -> selectedAnswerId = 5
            }
        }
        
        binding.buttonSubmit.setOnClickListener {
            submitAnswer()
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
                databaseManager.openDatabase()
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
        
        // Display question text
        binding.textViewQuestion.text = question.question
        
        // Display title if available
        if (!question.title.isNullOrBlank()) {
            binding.textViewTitle.text = question.title
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
        
        // Display answers
        displayAnswers()
        
        // Reset UI state
        selectedAnswerId = null
        binding.radioGroupAnswers.clearCheck()
        binding.textViewExplanation.visibility = android.view.View.GONE
        binding.buttonSubmit.isEnabled = true
        binding.buttonNext.isEnabled = currentQuestionIndex < questionIds.size - 1
        binding.buttonPrevious.isEnabled = currentQuestionIndex > 0
        
        // Hide explanation initially
        binding.textViewExplanation.text = ""
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
                radioButtons[index].text = answer.answerText
                radioButtons[index].visibility = android.view.View.VISIBLE
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
                
                // Show explanation
                val isCorrect = answerId == question.corrAns
                val resultText = if (isCorrect) "✓ Correct!" else "✗ Incorrect"
                val explanationText = "$resultText\n\nCorrect Answer: Answer ${question.corrAns}\n\n${question.explanation}"
                
                binding.textViewExplanation.text = explanationText
                binding.textViewExplanation.visibility = android.view.View.VISIBLE
                
                // Disable submit button
                binding.buttonSubmit.isEnabled = false
                
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
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        databaseManager.closeDatabase()
    }
}
