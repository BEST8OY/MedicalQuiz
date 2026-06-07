package com.medicalquiz.app.shared.viewmodel

import com.medicalquiz.app.shared.domain.ApplyFiltersUseCase
import com.medicalquiz.app.shared.domain.AppIntentSink
import com.medicalquiz.app.shared.domain.LoadQuestionUseCase
import com.medicalquiz.app.shared.domain.QuizSessionBoundaryUseCase
import com.medicalquiz.app.shared.domain.SnackbarSink

data class QuizViewModelDependencies(
    val quizSessionBoundaryUseCase: QuizSessionBoundaryUseCase,
    val applyFiltersUseCase: ApplyFiltersUseCase,
    val loadQuestionUseCase: LoadQuestionUseCase,
    val appIntentSink: AppIntentSink,
    val snackbarSink: SnackbarSink,
)
