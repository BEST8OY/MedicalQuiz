package com.medicalquiz.app.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.ui.AppTheme
import com.medicalquiz.app.shared.ui.DatabaseSelectionScreen
import com.medicalquiz.app.shared.ui.MediaHandler
import com.medicalquiz.app.shared.ui.QuizRoot
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

@Composable
fun App() {
    AppTheme {
        val viewModel = viewModel { QuizViewModel() }
        val settingsRepository = remember { SettingsRepository() }
        val cacheManager = remember { CacheManager() }
        
        var selectedDatabase by remember { mutableStateOf<String?>(null) }
        
        // Initialize common dependencies
        LaunchedEffect(Unit) {
            viewModel.setSettingsRepository(settingsRepository)
            viewModel.setCacheManager(cacheManager)
        }
        
        if (selectedDatabase == null) {
            DatabaseSelectionScreen(
                onDatabaseSelected = { dbName ->
                    selectedDatabase = dbName
                }
            )
        } else {
            // Initialize DB when selected
            LaunchedEffect(selectedDatabase) {
                selectedDatabase?.let { dbName ->
                    val dbPath = FileSystemHelper.getDatabasePath(dbName)
                    val databaseManager = DatabaseManager(dbPath)
                    databaseManager.init()
                    
                    viewModel.setDatabaseManager(databaseManager)
                    viewModel.setDatabaseName(dbName.removeSuffix(".db"))
                }
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
                },
                onChangeDatabase = {
                    selectedDatabase = null
                    viewModel.closeDatabase()
                }
            )
        }
    }
}
