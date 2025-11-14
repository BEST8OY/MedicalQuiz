package com.medicalquiz.app.ui

import android.graphics.Color
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
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
                correctAnswerId -> radioButton.setAnswerState(CORRECT_BG, CORRECT_TEXT)
                selectedAnswerId -> radioButton.setAnswerState(INCORRECT_BG, INCORRECT_TEXT)
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

    private fun RadioButton.setAnswerState(backgroundColor: Int, textColor: Int) {
        setBackgroundColor(backgroundColor)
        setTextColor(textColor)
    }

    private companion object {
        private val CORRECT_BG = Color.parseColor("#C8E6C9")
        private val CORRECT_TEXT = Color.parseColor("#1B5E20")
        private val INCORRECT_BG = Color.parseColor("#FFCDD2")
        private val INCORRECT_TEXT = Color.parseColor("#B71C1C")
    }
}
