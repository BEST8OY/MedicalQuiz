package com.medqb.app.shared.di

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DataStoreSettingsRepository
import com.medqb.app.shared.data.DefaultMarkedQuestionsRepository
import com.medqb.app.shared.data.DefaultQuizSessionRepository
import com.medqb.app.shared.data.DefaultTextHighlightsRepository
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MarkedQuestionsRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarDispatcher
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import com.medqb.app.shared.orchestration.AppStartupCoordinator
import com.medqb.app.shared.orchestration.AppWorkflowCoordinator
import com.medqb.app.shared.orchestration.MediaNavigationCoordinator
import com.medqb.app.shared.viewmodel.DatabaseSelectionViewModel
import com.medqb.app.shared.viewmodel.FilterHubViewModel
import com.medqb.app.shared.viewmodel.HtmlViewerViewModel
import com.medqb.app.shared.viewmodel.MediaViewerViewModel
import com.medqb.app.shared.viewmodel.QuizViewModel
import com.medqb.app.shared.viewmodel.SettingsViewModel
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val markedQuestionsRepository: MarkedQuestionsRepository
    val filterStateHolder: FilterStateHolder
    val snackbarDispatcher: SnackbarDispatcher
    val applyFiltersUseCase: ApplyFiltersUseCase
    val loadQuestionUseCase: LoadQuestionUseCase
    val historyCoordinator: AppHistoryCoordinator
    val startupCoordinator: AppStartupCoordinator
    val workflowCoordinator: AppWorkflowCoordinator
    val mediaNavigationCoordinator: MediaNavigationCoordinator

    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    fun bindQuizSessionRepository(impl: DefaultQuizSessionRepository): QuizSessionRepository

    @Binds
    fun bindTextHighlightsRepository(impl: DefaultTextHighlightsRepository): TextHighlightsRepository

    @Binds
    fun bindMarkedQuestionsRepository(impl: DefaultMarkedQuestionsRepository): MarkedQuestionsRepository

    @Binds
    fun bindSnackbarSink(impl: SnackbarDispatcher): SnackbarSink

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun createDatabaseSelectionViewModel(): DatabaseSelectionViewModel {
        return DatabaseSelectionViewModel(
            startupCoordinator = startupCoordinator
        )
    }

    fun createFilterHubViewModel(savedStateHandle: SavedStateHandle): FilterHubViewModel {
        return FilterHubViewModel(
            activeDatabaseHolder = activeDatabaseHolder,
            applyFiltersUseCase = applyFiltersUseCase,
            settingsRepository = settingsRepository,
            snackbarSink = snackbarDispatcher,
            filterStateHolder = filterStateHolder,
            savedStateHandle = savedStateHandle,
            historyCoordinator = historyCoordinator,
            sessionRepository = sessionRepository,
            markedQuestionsRepository = markedQuestionsRepository,
        )
    }

    fun createQuizViewModel(
        savedStateHandle: SavedStateHandle,
        route: MedQBRoutes.Quiz = MedQBRoutes.Quiz(),
    ): QuizViewModel {
        return QuizViewModel(
            route = route,
            settingsRepository = settingsRepository,
            textHighlightsRepository = textHighlightsRepository,
            markedQuestionsRepository = markedQuestionsRepository,
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

    fun createHtmlViewerViewModel(fileName: String): HtmlViewerViewModel {
        return HtmlViewerViewModel(
            fileName = fileName,
            localContentRepository = localContentRepository,
            settingsRepository = settingsRepository,
        )
    }

    fun createMediaViewerViewModel(): MediaViewerViewModel {
        return MediaViewerViewModel(
            mediaNavigationCoordinator = mediaNavigationCoordinator,
            settingsRepository = settingsRepository,
        )
    }
}
