package com.medicalquiz.app.ui

import android.graphics.Color
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.medicalquiz.app.R
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
        radioGroup.children.forEachIndexed { index, view ->
            val radioButton = view as? RadioButton ?: return@forEachIndexed
            val answerId = answers.getOrNull(index)?.answerId?.toInt() ?: return@forEachIndexed
            when (answerId) {
                correctAnswerId -> radioButton.setAnswerState(
                    R.color.quiz_answer_correct_bg,
                    R.color.quiz_answer_correct_text
                )
                selectedAnswerId -> radioButton.setAnswerState(
                    R.color.quiz_answer_incorrect_bg,
                    R.color.quiz_answer_incorrect_text
                )
                else -> radioButton.resetAnswerColor()
            }
        }
    }
    
    fun resetAnswerColors(radioGroup: RadioGroup) {
        radioGroup.children
            .mapNotNull { it as? RadioButton }
            .forEach { it.resetAnswerColor() }
    }
    
    private fun RadioButton.resetAnswerColor() {
        setBackgroundColor(Color.TRANSPARENT)
        val defaultTextColor = ContextCompat.getColor(context, android.R.color.darker_gray)
        setTextColor(defaultTextColor)
    }

    private fun RadioButton.setAnswerState(@ColorRes backgroundRes: Int, @ColorRes textRes: Int) {
        setBackgroundColor(ContextCompat.getColor(context, backgroundRes))
        setTextColor(ContextCompat.getColor(context, textRes))
    }
}
