package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.domain.RestoreSessionDecision
import com.medqb.app.shared.domain.RestoreSessionUseCase
import com.medqb.app.shared.navigation.QuizLaunchSource

/**
 * Coordinates app startup routines: listings available DBs, handling DB selections.
 */
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
    ): RestoreSessionDecision? {
        val dbName = selectedDatabase ?: return null
        return restoreSessionUseCase(
            dbName = dbName,
            initializedDatabase = initializedDatabase,
            pendingLaunchSource = pendingLaunchSource,
            shouldAttemptSessionRestore = shouldAttemptSessionRestore,
        )
    }
}
