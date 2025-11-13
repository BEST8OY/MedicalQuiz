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
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.medicalquiz.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseAdapter: DatabaseAdapter
    private val databases = mutableListOf<DatabaseItem>()
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupManageStorageButton()
        checkPermissionsAndLoadDatabases()
    }

    override fun onResume() {
        super.onResume()
        if (shouldRetryPermissionCheck) {
            checkPermissionsAndLoadDatabases()
            shouldRetryPermissionCheck = false
        }
    }

    private fun setupRecyclerView() {
        databaseAdapter = DatabaseAdapter(databases) { databaseItem ->
            onDatabaseSelected(databaseItem)
        }
        
        binding.recyclerViewDatabases.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = databaseAdapter
        }
    }

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

    private fun setupManageStorageButton() {
        binding.buttonGrantStorage.visibility = View.GONE
        binding.buttonGrantStorage.setOnClickListener {
            openManageStorageSettings()
        }
    }

    private fun hideManageStorageButton() {
        binding.buttonGrantStorage.visibility = View.GONE
    }

    private fun showManageStoragePrompt() {
        binding.textViewStatus.text = getString(R.string.storage_permission_required)
        binding.buttonGrantStorage.visibility = View.VISIBLE
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
        databases.clear()
        
        // Path: /storage/emulated/0/MedicalQuiz/databases
        val externalStoragePath = Environment.getExternalStorageDirectory()
        val databasesFolder = File(externalStoragePath, "MedicalQuiz/databases")
        
        if (!databasesFolder.exists()) {
            binding.textViewStatus.text = "No databases folder found at:\n${databasesFolder.absolutePath}"
            return
        }

        val dbFiles = databasesFolder.listFiles { file ->
            file.isFile && file.extension == "db"
        }

        if (dbFiles.isNullOrEmpty()) {
            binding.textViewStatus.text = "No .db files found in:\n${databasesFolder.absolutePath}"
            return
        }

        dbFiles.forEach { file ->
            databases.add(
                DatabaseItem(
                    name = file.nameWithoutExtension,
                    path = file.absolutePath,
                    size = formatFileSize(file.length())
                )
            )
        }

        databaseAdapter.notifyDataSetChanged()
        binding.textViewStatus.text = "Found ${databases.size} database(s)"
    }

    private fun onDatabaseSelected(databaseItem: DatabaseItem) {
        // Navigate to quiz screen with the selected database
        val intent = Intent(this, QuizActivity::class.java).apply {
            putExtra("DB_PATH", databaseItem.path)
            putExtra("DB_NAME", databaseItem.name)
        }
        startActivity(intent)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
