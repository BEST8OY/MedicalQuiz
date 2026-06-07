package com.medicalquiz.app.shared.di

import androidx.lifecycle.SavedStateHandle
import com.medicalquiz.app.shared.data.*
import com.medicalquiz.app.shared.domain.*
import com.medicalquiz.app.shared.navigation.*
import com.medicalquiz.app.shared.orchestration.*
import com.medicalquiz.app.shared.viewmodel.*
import kotlinx.coroutines.CoroutineScope

/**
 * Composition Root for manual dependency injection.
 * Manages the lifecycle of singletons and provides factories for scoped ViewModels.
 */
class AppDependencyContainer(val appScope: CoroutineScope) {
    
    // Core Shared Repositories & Data Sources
    val settingsRepository = SettingsRepository()
    val localContentRepository = LocalContentRepository()
    val userDataManager = UserDataManager()
    val cacheManager = CacheManager()
    val sessionRepository = QuizSessionRepository()
    val activeDatabaseHolder = ActiveDatabaseHolder()
    val uiEventDispatcher = UiEventDispatcher()
    
    val textHighlightsRepository = TextHighlightsRepository(
        userDataManager = userDataManager,
        scope = appScope
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
        uiEventDispatcher = uiEventDispatcher
    )
    
    // Orchestrators & Coordinators
    val navStateRepo = NavigationStateRepository()
    val navPersistenceCoordinator = AppNavigationPersistenceCoordinator(navStateRepo, sessionRepository)
    val historyCoordinator = AppHistoryCoordinator(sessionRepository, localContentRepository)
    val startupCoordinator = AppStartupCoordinator(
        localContentRepository = localContentRepository,
        sessionRepository = sessionRepository,
        restoreSessionUseCase = restoreSessionUseCase
    )

    // ViewModel Factory Methods
    
    fun createSettingsViewModel(): SettingsViewModel {
        return SettingsViewModel(settingsRepository)
    }

    fun createDatabaseSelectionViewModel(): DatabaseSelectionViewModel {
        return DatabaseSelectionViewModel(
            startupCoordinator = startupCoordinator,
            userDataManager = userDataManager,
            sessionRepository = sessionRepository
        )
    }

    fun createFilterViewModel(): FilterViewModel {
        return FilterViewModel(
            activeDatabaseHolder = activeDatabaseHolder,
            historyCoordinator = historyCoordinator,
            applyFiltersUseCase = applyFiltersUseCase,
            sessionRepository = sessionRepository,
            uiEventDispatcher = uiEventDispatcher,
            appScope = appScope,
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
