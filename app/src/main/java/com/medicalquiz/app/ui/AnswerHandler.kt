package com.medicalquiz.app.ui

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.card.MaterialCardView
import com.medicalquiz.app.data.models.Answer

/**
 * Handler for answer selection and highlighting
 */
class AnswerHandler {
    
    fun highlightAnswers(
        radioGroup: RadioGroup,
        answers: List<Answer>,
        selectedAnswerId: Int,
        correctAnswerId: Int
    ) {
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as? RadioButton ?: continue
            val answer = answers.getOrNull(i) ?: continue
            
            val cardView = radioButton.parent.parent as? MaterialCardView ?: continue
            
            when {
                answer.answerId.toInt() == correctAnswerId -> {
                    // Correct answer - green
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#C8E6C9"))
                    radioButton.setTextColor(android.graphics.Color.parseColor("#1B5E20"))
                }
                answer.answerId.toInt() == selectedAnswerId && selectedAnswerId != correctAnswerId -> {
                    // Wrong selected answer - red
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
                    radioButton.setTextColor(android.graphics.Color.parseColor("#B71C1C"))
                }
                else -> {
                    // Other answers - default
                    resetAnswerColor(cardView, radioButton)
                }
            }
        }
    }
    
    fun resetAnswerColors(radioGroup: RadioGroup) {
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as? RadioButton ?: continue
            val cardView = radioButton.parent.parent as? MaterialCardView ?: continue
            resetAnswerColor(cardView, radioButton)
        }
    }
    
    private fun resetAnswerColor(cardView: MaterialCardView, radioButton: RadioButton) {
        cardView.setCardBackgroundColor(android.graphics.Color.WHITE)
        radioButton.setTextColor(android.graphics.Color.parseColor("#212121"))
    }
}
