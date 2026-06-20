package com.medqb.app.shared.di

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.*
import com.medqb.app.shared.domain.*
import com.medqb.app.shared.navigation.*
import com.medqb.app.shared.orchestration.*
import com.medqb.app.shared.viewmodel.*

/**
 * Composition Root for manual dependency injection.
 * Manages the lifecycle of singletons and provides factories for scoped ViewModels.
 */
class AppDependencyContainer {
    
    // Core Shared Repositories & Data Sources
    val settingsRepository = SettingsRepository()
    val localContentRepository = LocalContentRepository()
    val userDataManager = UserDataManager()
    val cacheManager = CacheManager()
    val sessionRepository = QuizSessionRepository()
    val activeDatabaseHolder = ActiveDatabaseHolder()
    
    val appIntentDispatcher = AppIntentDispatcher()
    val snackbarDispatcher = SnackbarDispatcher()
    
    val textHighlightsRepository = TextHighlightsRepository(
        userDataManager = userDataManager
    )
    
    // Use Cases
    val restoreSessionUseCase = RestoreSessionUseCase(activeDatabaseHolder, sessionRepository)
    val applyFiltersUseCase = ApplyFiltersUseCase()
    val loadQuestionUseCase = LoadQuestionUseCase()
    
    val quizSessionBoundaryUseCase = QuizSessionBoundaryUseCase(sessionRepository)
    
    val quizViewModelDependencies = QuizViewModelDependencies(
        quizSessionBoundaryUseCase = quizSessionBoundaryUseCase,
        applyFiltersUseCase = applyFiltersUseCase,
        loadQuestionUseCase = loadQuestionUseCase,
        appIntentSink = appIntentDispatcher,
        snackbarSink = snackbarDispatcher
    )
    
    // Orchestrators & Coordinators
    val navPersistenceCoordinator = AppNavigationPersistenceCoordinator(sessionRepository)
    val historyCoordinator = AppHistoryCoordinator(sessionRepository, localContentRepository)
    val startupCoordinator = AppStartupCoordinator(
        localContentRepository = localContentRepository,
        sessionRepository = sessionRepository,
        restoreSessionUseCase = restoreSessionUseCase
    )
    val workflowCoordinator = AppWorkflowCoordinator(startupCoordinator)
    val mediaNavigationCoordinator = MediaNavigationCoordinator(localContentRepository)

    // ViewModel Factory Methods
    
    fun createDatabaseSelectionViewModel(): DatabaseSelectionViewModel {
        return DatabaseSelectionViewModel(
            startupCoordinator = startupCoordinator,
            userDataManager = userDataManager
        )
    }

    fun createFilterViewModel(): FilterViewModel {
        return FilterViewModel(
            activeDatabaseHolder = activeDatabaseHolder,
            applyFiltersUseCase = applyFiltersUseCase,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository,
            snackbarSink = snackbarDispatcher,
        )
    }

    fun createHistoryViewModel(): HistoryViewModel {
        return HistoryViewModel(
            activeDatabaseHolder = activeDatabaseHolder,
            historyCoordinator = historyCoordinator,
            sessionRepository = sessionRepository,
        )
    }

    fun createQuizViewModel(savedStateHandle: SavedStateHandle): QuizViewModel {
        return QuizViewModel(
            settingsRepository = settingsRepository,
            textHighlightsRepository = textHighlightsRepository,
            cacheManager = cacheManager,
            savedStateHandle = savedStateHandle,
            dependencies = quizViewModelDependencies,
            activeDatabaseHolder = activeDatabaseHolder
        )
    }
}
