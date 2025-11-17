package com.medicalquiz.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

class MainActivity : AppCompatActivity() {
    private var databaseList by mutableStateOf<List<DatabaseItem>>(emptyList())
    private var statusText by mutableStateOf("")
    private var showManageStoragePrompt by mutableStateOf(false)
    private var shouldRetryPermissionCheck = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadDatabases()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        shouldRetryPermissionCheck = false
        checkPermissionsAndLoadDatabases()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs storage permission to access database files. Please grant permission in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use Jetpack Compose for the main UI — convert the database list to a LazyColumn
        setContent {
            com.medicalquiz.app.ui.MainScreen(
                databases = databaseList,
                statusText = statusText,
                showManageStoragePrompt = showManageStoragePrompt,
                onGrantStorage = { openManageStorageSettings() },
                onDatabaseSelected = { onDatabaseSelected(it) }
            )
        }

        // Compose handles the list & manage storage prompt UI
        checkPermissionsAndLoadDatabases()
    }

    override fun onResume() {
        super.onResume()
        if (shouldRetryPermissionCheck) {
            checkPermissionsAndLoadDatabases()
            shouldRetryPermissionCheck = false
        }
    }

    // RecyclerView removed — Compose LazyColumn is used for the database list

    private fun checkPermissionsAndLoadDatabases() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    hideManageStorageButton()
                    loadDatabases()
                } else {
                    showManageStoragePrompt()
                }
            }
            else -> {
                if (
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    loadDatabases()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    // Manage storage button is provided by the Compose MainScreen and handled there.

    private fun hideManageStorageButton() {
        showManageStoragePrompt = false
    }

    private fun showManageStoragePrompt() {
        statusText = getString(R.string.storage_permission_required)
        showManageStoragePrompt = true
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openManageStorageSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        shouldRetryPermissionCheck = true
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageStorageLauncher.launch(intent)
        }
    }

    private fun loadDatabases() {
        val mutableDatabases = mutableListOf<DatabaseItem>()
        
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            statusText = "External storage not available"
            return
        }
        
        // Path: /storage/emulated/0/MedicalQuiz/databases
        val externalStoragePath = Environment.getExternalStorageDirectory()
        val databasesFolder = File(externalStoragePath, Constants.DATABASES_FOLDER)
        
        if (!databasesFolder.exists()) {
            statusText = "No databases folder found at:\n${databasesFolder.absolutePath}"
            return
        }

        val dbFiles = try {
            databasesFolder.listFiles { file ->
                file.isFile && file.extension == "db"
            }
        } catch (e: Exception) {
            statusText = "Error accessing databases folder: ${e.message}"
            return
        }

        if (dbFiles.isNullOrEmpty()) {
            statusText = "No .db files found in:\n${databasesFolder.absolutePath}"
            return
        }

        dbFiles.forEach { file ->
            mutableDatabases.add(
                DatabaseItem(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    size = formatFileSize(file.length())
                )
            )
        }

        // Update state for compose UI
        databaseList = mutableDatabases.toList()
        statusText = "Found ${mutableDatabases.size} database(s)"
        showManageStoragePrompt = false
    }

    private fun onDatabaseSelected(databaseItem: DatabaseItem) {
        // Navigate to quiz screen with the selected database
        val intent = Intent(this, QuizActivity::class.java).apply {
            putExtra("DB_PATH", databaseItem.path)
            putExtra("DB_NAME", databaseItem.name)
            putExtra(QuizActivity.EXTRA_OPEN_FILTERS_FULLSCREEN, true)
        }
        startActivity(intent)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
