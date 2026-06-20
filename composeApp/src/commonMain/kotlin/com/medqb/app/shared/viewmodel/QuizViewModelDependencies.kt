package com.medqb.app.shared.viewmodel

import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.AppIntentSink
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarSink

data class QuizViewModelDependencies(
    val applyFiltersUseCase: ApplyFiltersUseCase,
    val loadQuestionUseCase: LoadQuestionUseCase,
    val appIntentSink: AppIntentSink,
    val snackbarSink: SnackbarSink,
)
