package com.medqb.app.shared.domain

import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import dev.zacsweers.metro.Inject

@Inject
class LoadQuestionUseCase {

    suspend operator fun invoke(
        db: DatabaseProvider?,
        questionId: Long,
        isLoggingEnabled: Boolean,
    ): LoadQuestionResult {
        if (db == null) return LoadQuestionResult(null, emptyList(), null)

        val (question, answers, performance) = db.getQuestionWithDetails(
            questionId = questionId,
            loadPerformance = isLoggingEnabled,
        )

        // Skip performance when the question doesn't exist — matches pre-refactor behavior
        // and avoids a wasted logs roundtrip for an invalid question id.
        return LoadQuestionResult(
            question = question,
            answers = answers,
            performance = if (question == null) null else performance,
        )
    }
}

data class LoadQuestionResult(
    val question: Question?,
    val answers: List<Answer>,
    val performance: QuestionPerformance?,
)
