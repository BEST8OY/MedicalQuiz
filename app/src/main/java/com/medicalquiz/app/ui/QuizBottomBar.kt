package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun QuizBottomBar(viewModel: com.medicalquiz.app.viewmodel.QuizViewModel, onJumpTo: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = { viewModel.loadPrevious() }, enabled = state.hasPreviousQuestion) {
            Text("Previous")
        }

        Row {
            Text(text = "${state.currentQuestionIndex + 1}")
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "/ ${state.totalQuestions}")
        }

        Button(onClick = { viewModel.loadNext() }, enabled = state.hasNextQuestion) {
            Text("Next")
        }
    }
}
