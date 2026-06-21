package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.orchestration.AppStartupCoordinator
import dev.zacsweers.metro.Inject
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

    init {
        initializeApp()
    }

    fun initializeApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val dbs = startupCoordinator.initializeApp(userDataManager)
            _availableDatabases.value = dbs
            _isLoading.value = false
        }
    }

    fun refreshDatabases() {
        viewModelScope.launch {
            _isLoading.value = true
            val dbs = startupCoordinator.refreshDatabases()
            _availableDatabases.value = dbs
            _isLoading.value = false
        }
    }
}
