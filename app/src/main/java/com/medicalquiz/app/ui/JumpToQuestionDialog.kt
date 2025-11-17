package com.medicalquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun JumpToQuestionDialog(
    totalQuestions: Int,
    currentQuestionIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(currentQuestionIndex) }
    var inputText by remember { mutableStateOf((currentQuestionIndex + 1).toString()) }
    var useGrid by remember { mutableStateOf(totalQuestions <= 200) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Column {
                    Text(
                        text = "Jump to Question",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total questions: $totalQuestions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Input Method Selection
                if (totalQuestions > 50) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { useGrid = true },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (useGrid) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Text(
                                "Grid",
                                color = if (useGrid) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (useGrid) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        TextButton(
                            onClick = { useGrid = false },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (!useGrid) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            Text(
                                "Scroll",
                                color = if (!useGrid) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (!useGrid) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Input Method: Grid or Scrollable List
                if (useGrid && totalQuestions <= 200) {
                    // Grid View for smaller datasets
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items((1..totalQuestions).toList()) { questionNumber ->
                            QuestionNumberButton(
                                number = questionNumber,
                                isSelected = selectedIndex == questionNumber - 1,
                                onClick = { selectedIndex = questionNumber - 1 }
                            )
                        }
                    }
                } else {
                    // Scrollable list with input field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { newValue ->
                                inputText = newValue
                                val num = newValue.toIntOrNull()
                                if (num != null && num in 1..totalQuestions) {
                                    selectedIndex = num - 1
                                }
                            },
                            label = { Text("Question Number") },
                            placeholder = { Text("1-$totalQuestions") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp
                            )
                        )

                        // Current question display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${selectedIndex + 1} / $totalQuestions",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Accelerating scrollable list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            repeat(totalQuestions) { index ->
                                val questionNum = index + 1
                                val isSelected = selectedIndex == index
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                        .clickable { selectedIndex = index }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            else if (index % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(4.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (index % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 2.dp else 0.dp
                                    )
                                ) {
                                    Text(
                                        text = questionNum.toString(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onJumpTo(selectedIndex)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Jump to ${selectedIndex + 1}")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionNumberButton(
    number: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
