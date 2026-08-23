package com.medqb.app.shared.domain

import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.QuestionDetails
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loads the fully-resolved payload for one question: question text, answers,
 * derived correct answer, performance stats, and saved highlights.
 *
 * Content (quiz database) and highlights (user database) live in independent
 * stores, so both reads run concurrently. A highlight read failure is logged and
 * degraded to an empty list — it must never fail the question load itself.
 */
@Inject
class LoadQuestionUseCase(
    private val textHighlightsRepository: TextHighlightsRepository,
) {

    suspend operator fun invoke(
        db: DatabaseProvider?,
        dbName: String,
        questionId: Long,
        isLoggingEnabled: Boolean,
    ): QuestionDetails {
        if (db == null) {
            return QuestionDetails(question = null, answers = emptyList(), performance = null, correctAnswerId = null)
        }

        val (details, highlights) = coroutineScope {
            val detailsDeferred = async {
                db.getQuestionWithDetails(
                    questionId = questionId,
                    loadPerformance = isLoggingEnabled,
                )
            }
            val highlightsDeferred = async {
                if (dbName.isEmpty()) {
                    emptyList()
                } else {
                    try {
                        textHighlightsRepository.getHighlightsForQuestion(dbName, questionId)
                    } catch (e: Exception) {
                        Logger.e("LoadQuestionUseCase", "Error loading highlights for question $questionId", e)
                        emptyList()
                    }
                }
            }
            detailsDeferred.await() to highlightsDeferred.await()
        }

        // Skip performance when the question doesn't exist — avoids a wasted logs
        // roundtrip for an invalid question id.
        return if (details.question == null) {
            details.copy(performance = null)
        } else {
            details.copy(
                questionHighlights = highlights.filter { it.section == HighlightSection.QUESTION },
                explanationHighlights = highlights.filter { it.section == HighlightSection.EXPLANATION },
            )
        }
    }
}
