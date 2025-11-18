package com.medicalquiz.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JumpToQuestionDialog(
    totalQuestions: Int,
    currentQuestionIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf((currentQuestionIndex + 1).toString()) }
    var hasError by remember { mutableStateOf(false) }

    val isValidInput = inputValue.isNotEmpty() && inputValue.toIntOrNull() != null
    val questionNumber = inputValue.toIntOrNull() ?: 0
    val isInRange = isValidInput && questionNumber in 1..totalQuestions
    val currentQuestion = currentQuestionIndex + 1

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Jump to Question",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Navigate to a specific question",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Current Progress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "$currentQuestion of $totalQuestions",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Progress indicator
                    Text(
                        text = "${((currentQuestion.toFloat() / totalQuestions) * 100).toInt()}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Input Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Question Number",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        inputValue = newValue
                        hasError = newValue.isNotEmpty() && (newValue.toIntOrNull() == null || 
                                  newValue.toIntOrNull()?.let { it !in 1..totalQuestions } ?: true)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    label = { Text("Enter question number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hasError && inputValue.isNotEmpty(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        errorIndicatorColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        when {
                            inputValue.isEmpty() -> null
                            isInRange -> {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Valid",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Invalid",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                )

                // Error Message with Animation
                if (hasError && inputValue.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier
                                    .height(18.dp)
                                    .padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = when {
                                    inputValue.toIntOrNull() == null -> 
                                        "Please enter a valid number"
                                    else -> 
                                        "Question must be between 1 and $totalQuestions"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Range Info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier
                                .height(18.dp)
                                .padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Enter a number from 1 to $totalQuestions",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { 
                        if (isInRange) {
                            onJumpTo(questionNumber - 1)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isInRange
                ) {
                    Text(
                        text = "Jump",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
