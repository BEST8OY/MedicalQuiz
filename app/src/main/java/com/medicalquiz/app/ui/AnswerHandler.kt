package com.medicalquiz.app.ui

import android.graphics.Color
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.view.children
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
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
                    MaterialR.attr.colorTertiaryContainer,
                    MaterialR.attr.colorOnTertiaryContainer
                )
                selectedAnswerId -> radioButton.setAnswerState(
                    MaterialR.attr.colorErrorContainer,
                    MaterialR.attr.colorOnErrorContainer
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
        val defaultTextColor = MaterialColors.getColor(
            this,
            MaterialR.attr.colorOnSurface,
            Color.DKGRAY
        )
        setTextColor(defaultTextColor)
    }

    private fun RadioButton.setAnswerState(backgroundAttr: Int, textAttr: Int) {
        val backgroundColor = MaterialColors.getColor(this, backgroundAttr, Color.LTGRAY)
        val textColor = MaterialColors.getColor(this, textAttr, Color.BLACK)
        setBackgroundColor(backgroundColor)
        setTextColor(textColor)
    }
}
