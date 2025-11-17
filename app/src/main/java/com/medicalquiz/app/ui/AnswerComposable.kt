package com.medicalquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.medicalquiz.app.data.models.Answer

@Composable
fun AnswersList(
    answers: List<Answer>,
    selectedAnswerId: Int?,
    correctAnswerId: Int?,
    onAnswerSelected: (Long) -> Unit
) {
    Column {
        answers.forEach { answer ->
            val isSelected = (answer.answerId.toInt() == selectedAnswerId)
            val isCorrect = (answer.answerId.toInt() == correctAnswerId)
            val bg = when {
                isCorrect -> Color(0xFFCCFFCC)
                isSelected -> Color(0xFFFFCCCC)
                else -> Color.Transparent
            }
            Text(
                text = answer.answerText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .clickable { onAnswerSelected(answer.answerId) }
                    .padding(12.dp)
            )
        }
    }
}
