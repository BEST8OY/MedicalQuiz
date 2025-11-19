package com.medicalquiz.app.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.ui.MediaHandler
import com.medicalquiz.app.shared.ui.QuizRoot
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

@Composable
fun App() {
    MaterialTheme {
        val viewModel = viewModel { QuizViewModel() }
        val settingsRepository = remember { SettingsRepository() }
        val cacheManager = remember { CacheManager() }
        
        // Initialize dependencies
        LaunchedEffect(Unit) {
            val dbPath = FileSystemHelper.getDatabasePath("medical_quiz.db")
            val databaseManager = DatabaseManager(dbPath)
            databaseManager.init()
            
            viewModel.setDatabaseManager(databaseManager)
            viewModel.setSettingsRepository(settingsRepository)
            viewModel.setCacheManager(cacheManager)
        }
        
        val mediaHandler = remember { 
            MediaHandler(
                onOpenMedia = { files, index ->
                    viewModel.openMedia(files, index)
                }
            ) 
        }

        QuizRoot(
            viewModel = viewModel,
            mediaHandler = mediaHandler,
            onClearFilters = {
                viewModel.setSelectedSubjects(emptySet())
                viewModel.setSelectedSystems(emptySet())
            },
            onStart = {
                viewModel.loadFilteredQuestionIds()
                viewModel.loadQuestion(0)
            }
        )
    }
}
