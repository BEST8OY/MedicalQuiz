package com.medicalquiz.app.ui

import android.widget.RadioButton
import android.widget.RadioGroup
import com.medicalquiz.app.data.models.Answer
import android.graphics.Color

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
            when {
                answer.answerId.toInt() == correctAnswerId -> {
                    setAnswerState(
                        radioButton,
                        Color.parseColor("#C8E6C9"),
                        Color.parseColor("#1B5E20")
                    )
                }
                answer.answerId.toInt() == selectedAnswerId && selectedAnswerId != correctAnswerId -> {
                    setAnswerState(
                        radioButton,
                        Color.parseColor("#FFCDD2"),
                        Color.parseColor("#B71C1C")
                    )
                }
                else -> {
                    resetAnswerColor(radioButton)
                }
            }
        }
    }
    
    fun resetAnswerColors(radioGroup: RadioGroup) {
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as? RadioButton ?: continue
            resetAnswerColor(radioButton)
        }
    }
    
    private fun resetAnswerColor(radioButton: RadioButton) {
        radioButton.setBackgroundColor(Color.TRANSPARENT)
        // Reset to theme default text color
        radioButton.setTextColor(radioButton.context.getColor(android.R.color.darker_gray))
    }

    private fun setAnswerState(radioButton: RadioButton, backgroundColor: Int, textColor: Int) {
        radioButton.setBackgroundColor(backgroundColor)
        radioButton.setTextColor(textColor)
    }
}
