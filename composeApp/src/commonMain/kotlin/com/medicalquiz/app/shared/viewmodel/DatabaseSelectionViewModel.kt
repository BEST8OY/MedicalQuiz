package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.UserDataManager
import com.medicalquiz.app.shared.orchestration.AppStartupCoordinator
import com.medicalquiz.app.shared.platform.FolderPicker
import com.medicalquiz.app.shared.platform.MediaResolver
import com.medicalquiz.app.shared.platform.SafImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DatabaseSelectionViewModel(
    private val startupCoordinator: AppStartupCoordinator,
    private val userDataManager: UserDataManager,
) : ViewModel() {

    private val _availableDatabases = MutableStateFlow<List<String>>(emptyList())
    val availableDatabases: StateFlow<List<String>> = _availableDatabases.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasFolder = MutableStateFlow(FolderPicker.hasPersistedFolder())
    val hasFolder: StateFlow<Boolean> = _hasFolder.asStateFlow()

    init {
        initializeApp()
    }

    fun initializeApp() {
        viewModelScope.launch {
            _isLoading.value = true
            if (FolderPicker.hasPersistedFolder()) {
                MediaResolver.init()
                val dbs = startupCoordinator.initializeApp(userDataManager)
                _availableDatabases.value = dbs
            } else {
                _availableDatabases.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun onFolderPicked() {
        _hasFolder.value = true
        viewModelScope.launch {
            _isLoading.value = true
            SafImporter.importDatabases()
            MediaResolver.init()
            val dbs = startupCoordinator.refreshDatabases()
            _availableDatabases.value = dbs
            _isLoading.value = false
        }
    }

    fun resyncFolder() {
        viewModelScope.launch {
            _isLoading.value = true
            if (FolderPicker.hasPersistedFolder()) {
                SafImporter.importDatabases()
                MediaResolver.init()
                val dbs = startupCoordinator.refreshDatabases()
                _availableDatabases.value = dbs
            }
            _isLoading.value = false
        }
    }
}
