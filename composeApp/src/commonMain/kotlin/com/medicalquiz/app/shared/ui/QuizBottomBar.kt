package com.medicalquiz.app.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

@Composable
fun QuizBottomBar(viewModel: QuizViewModel, onJumpTo: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { viewModel.loadPrevious() }, enabled = state.hasPreviousQuestion) {
            Text("Previous")
        }

        // Question counter - clickable to jump to specific question
        Row(
            modifier = Modifier
                .clickable(
                    onClick = onJumpTo,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No ripple for now
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "${state.currentQuestionIndex + 1}")
            Text(text = " / ${state.totalQuestions}")
        }

        Button(onClick = { viewModel.loadNext() }, enabled = state.hasNextQuestion) {
            Text("Next")
        }
    }
}
