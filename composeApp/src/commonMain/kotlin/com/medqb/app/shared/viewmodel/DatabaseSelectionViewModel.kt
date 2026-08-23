package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.orchestration.AppStartupCoordinator
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Scoped ViewModel for the Database Selection Screen.
 * Lists available SQLite databases and prepares them.
 */
@Inject
class DatabaseSelectionViewModel(
    private val startupCoordinator: AppStartupCoordinator,
    private val userDataManager: UserDataManager,
) : ViewModel() {

    private val _availableDatabases = MutableStateFlow<List<String>>(emptyList())
    val availableDatabases: StateFlow<List<String>> = _availableDatabases.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Serializes init load vs user-triggered refresh; latest request wins.
    private var loadJob: Job? = null

    init {
        initializeApp()
    }

    private fun initializeApp() {
        loadDatabases { startupCoordinator.initializeApp(userDataManager) }
    }

    fun refreshDatabases() {
        loadDatabases { startupCoordinator.refreshDatabases() }
    }

    private fun loadDatabases(block: suspend () -> List<String>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _availableDatabases.value = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("DatabaseSelectionViewModel", "Error loading database list", e)
                _errorMessage.value = "Couldn't load QBanks: ${e.message ?: "unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
