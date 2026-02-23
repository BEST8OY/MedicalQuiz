package com.medicalquiz.app.shared.orchestration

import com.medicalquiz.app.shared.data.LocalContentRepository
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.UserDataManager
import com.medicalquiz.app.shared.domain.RestoreSessionDecision
import com.medicalquiz.app.shared.domain.RestoreSessionUseCase
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

class AppStartupCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val sessionRepository: QuizSessionRepository,
    private val restoreSessionUseCase: RestoreSessionUseCase,
) {
    suspend fun initializeApp(userDataManager: UserDataManager): List<String> {
        userDataManager.init()
        val databases = localContentRepository.listDatabases()
        sessionRepository.refreshHistoryAsync()
        return databases
    }

    suspend fun refreshDatabases(): List<String> = localContentRepository.listDatabases()

    suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
        initializedDatabase: String?,
        pendingLaunchSource: QuizLaunchSource?,
        shouldAttemptSessionRestore: Boolean,
        viewModel: QuizViewModel,
    ): RestoreSessionDecision? {
        val dbName = selectedDatabase ?: return null
        return restoreSessionUseCase(
            dbName = dbName,
            initializedDatabase = initializedDatabase,
            pendingLaunchSource = pendingLaunchSource,
            shouldAttemptSessionRestore = shouldAttemptSessionRestore,
            viewModel = viewModel,
        )
    }
}
