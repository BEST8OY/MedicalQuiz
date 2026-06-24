package com.medqb.app.shared.di

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.domain.AppIntentDispatcher
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarDispatcher
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import com.medqb.app.shared.orchestration.AppStartupCoordinator
import com.medqb.app.shared.orchestration.AppWorkflowCoordinator
import com.medqb.app.shared.orchestration.MediaNavigationCoordinator
import com.medqb.app.shared.viewmodel.DatabaseSelectionViewModel
import com.medqb.app.shared.viewmodel.FilterViewModel
import com.medqb.app.shared.viewmodel.HistoryViewModel
import com.medqb.app.shared.viewmodel.QuizViewModel
import com.medqb.app.shared.viewmodel.SettingsViewModel

/**
 * Common dependency graph interface.
 * Platform-specific graphs extend this and add @DependencyGraph.
 */
interface AppGraph {
    val activeDatabaseHolder: ActiveDatabaseHolder
    val settingsRepository: SettingsRepository
    val localContentRepository: LocalContentRepository
    val userDataManager: UserDataManager
    val sessionRepository: QuizSessionRepository
    val textHighlightsRepository: TextHighlightsRepository
    val filterStateHolder: FilterStateHolder
    val appIntentDispatcher: AppIntentDispatcher
    val snackbarDispatcher: SnackbarDispatcher
    val applyFiltersUseCase: ApplyFiltersUseCase
    val loadQuestionUseCase: LoadQuestionUseCase
    val historyCoordinator: AppHistoryCoordinator
    val startupCoordinator: AppStartupCoordinator
    val workflowCoordinator: AppWorkflowCoordinator
    val mediaNavigationCoordinator: MediaNavigationCoordinator

    fun createDatabaseSelectionViewModel(): DatabaseSelectionViewModel {
        return DatabaseSelectionViewModel(
            startupCoordinator = startupCoordinator,
            userDataManager = userDataManager
        )
    }

    fun createFilterViewModel(savedStateHandle: SavedStateHandle): FilterViewModel {
        return FilterViewModel(
            activeDatabaseHolder = activeDatabaseHolder,
            applyFiltersUseCase = applyFiltersUseCase,
            settingsRepository = settingsRepository,
            snackbarSink = snackbarDispatcher,
            filterStateHolder = filterStateHolder,
            savedStateHandle = savedStateHandle,
        )
    }

    fun createHistoryViewModel(): HistoryViewModel {
        return HistoryViewModel(
            historyCoordinator = historyCoordinator,
            sessionRepository = sessionRepository,
        )
    }

    fun createQuizViewModel(savedStateHandle: SavedStateHandle): QuizViewModel {
        return QuizViewModel(
            settingsRepository = settingsRepository,
            textHighlightsRepository = textHighlightsRepository,
            sessionRepository = sessionRepository,
            savedStateHandle = savedStateHandle,
            activeDatabaseHolder = activeDatabaseHolder,
            loadQuestionUseCase = loadQuestionUseCase,
            snackbarSink = snackbarDispatcher,
            filterStateHolder = filterStateHolder,
        )
    }

    fun createSettingsViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepository
        )
    }
}
