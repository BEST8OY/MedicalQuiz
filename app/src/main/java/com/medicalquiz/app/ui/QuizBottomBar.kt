package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun QuizBottomBar(viewModel: com.medicalquiz.app.viewmodel.QuizViewModel, onJumpTo: () -> Unit) {
    android.util.Log.d("QuizBottomBar", "QuizBottomBar rendering")
    val state by viewModel.state.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Button(onClick = { viewModel.loadPrevious() }, enabled = state.hasPreviousQuestion) {
            Text("Previous")
        }

        // Question counter - clickable to jump to specific question
        Row(
            modifier = Modifier
                .clickable(
                    onClick = onJumpTo,
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text = "${state.currentQuestionIndex + 1}")
            Text(text = " / ${state.totalQuestions}")
        }

        Button(onClick = { viewModel.loadNext() }, enabled = state.hasNextQuestion) {
            Text("Next")
        }
    }
}
