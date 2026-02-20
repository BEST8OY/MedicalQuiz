package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question

class LoadQuestionUseCase {

    suspend operator fun invoke(
        db: DatabaseProvider?,
        questionId: Long,
        isLoggingEnabled: Boolean,
    ): LoadQuestionResult {
        val question = db?.getQuestionById(questionId)
        val answers = db?.getAnswersForQuestion(questionId) ?: emptyList()
        val performance = if (isLoggingEnabled && question != null) {
            db.getQuestionPerformance(question.id)
        } else {
            null
        }

        return LoadQuestionResult(
            question = question,
            answers = answers,
            performance = performance,
        )
    }
}

data class LoadQuestionResult(
    val question: Question?,
    val answers: List<Answer>,
    val performance: QuestionPerformance?,
)
