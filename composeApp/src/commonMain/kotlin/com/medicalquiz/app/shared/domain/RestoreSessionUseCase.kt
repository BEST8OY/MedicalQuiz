package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

class RestoreSessionUseCase {

    suspend operator fun invoke(
        dbName: String,
        initializedDatabase: String?,
        pendingLaunchSource: QuizLaunchSource?,
        shouldAttemptSessionRestore: Boolean,
        viewModel: QuizViewModel,
    ): RestoreSessionDecision {
        val resolvedInitializedDatabase = ensureDatabaseInitialized(
            dbName = dbName,
            initializedDatabase = initializedDatabase,
            viewModel = viewModel,
        )

        if (pendingLaunchSource == QuizLaunchSource.History) {
            return when (viewModel.restoreSession()) {
                QuizViewModel.SessionRestoreResult.Restored -> {
                    viewModel.loadFilteredQuestionIds()
                    RestoreSessionDecision(
                        initializedDatabase = resolvedInitializedDatabase,
                        pendingLaunchSource = null,
                        shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                        shouldPopToDatabaseSelection = false,
                    )
                }

                QuizViewModel.SessionRestoreResult.DatabaseMismatch,
                QuizViewModel.SessionRestoreResult.NoSession -> {
                    viewModel.setLoadingState(false)
                    RestoreSessionDecision(
                        initializedDatabase = resolvedInitializedDatabase,
                        pendingLaunchSource = null,
                        shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                        shouldPopToDatabaseSelection = true,
                    )
                }
            }
        }

        val currentState = viewModel.state.value
        if (shouldAttemptSessionRestore && currentState.questionIds.isEmpty() && !currentState.isLoading) {
            return when (viewModel.restoreSession()) {
                QuizViewModel.SessionRestoreResult.Restored -> {
                    viewModel.loadFilteredQuestionIds()
                    RestoreSessionDecision(
                        initializedDatabase = resolvedInitializedDatabase,
                        pendingLaunchSource = pendingLaunchSource,
                        shouldAttemptSessionRestore = false,
                        shouldPopToDatabaseSelection = false,
                    )
                }

                QuizViewModel.SessionRestoreResult.DatabaseMismatch,
                QuizViewModel.SessionRestoreResult.NoSession -> {
                    RestoreSessionDecision(
                        initializedDatabase = resolvedInitializedDatabase,
                        pendingLaunchSource = pendingLaunchSource,
                        shouldAttemptSessionRestore = false,
                        shouldPopToDatabaseSelection = true,
                    )
                }
            }
        }

        return RestoreSessionDecision(
            initializedDatabase = resolvedInitializedDatabase,
            pendingLaunchSource = pendingLaunchSource,
            shouldAttemptSessionRestore = shouldAttemptSessionRestore,
            shouldPopToDatabaseSelection = false,
        )
    }

    private suspend fun ensureDatabaseInitialized(
        dbName: String,
        initializedDatabase: String?,
        viewModel: QuizViewModel,
    ): String {
        val hasDatabaseManager = viewModel.getDatabaseManager() != null
        if (initializedDatabase == dbName && hasDatabaseManager) return dbName

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = DatabaseManager(dbPath)
        databaseManager.init()

        viewModel.setDatabaseManager(databaseManager)
        viewModel.setDatabaseName(dbName.removeSuffix(".db"))
        return dbName
    }
}

data class RestoreSessionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
    val shouldAttemptSessionRestore: Boolean,
    val shouldPopToDatabaseSelection: Boolean,
)
