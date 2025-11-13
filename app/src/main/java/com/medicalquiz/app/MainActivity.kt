package com.medicalquiz.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.medicalquiz.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseAdapter: DatabaseAdapter
    private val databases = mutableListOf<DatabaseItem>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadDatabases()
        } else {
            Toast.makeText(
                this,
                "Storage permission is required to access database files",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissionsAndLoadDatabases()
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
                // Android 11+: Check if we have all files access
                if (Environment.isExternalStorageManager()) {
                    loadDatabases()
                } else {
                    // Request permission for all files access
                    requestStoragePermission()
                }
            }
            else -> {
                // Android 10 and below
                if (ContextCompat.checkSelfPermission(
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

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, we'd need to use ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            // For this basic implementation, we'll use scoped storage approach
            Toast.makeText(
                this,
                "Please grant storage access in app settings",
                Toast.LENGTH_LONG
            ).show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        Toast.makeText(
            this,
            "Selected: ${databaseItem.name}",
            Toast.LENGTH_SHORT
        ).show()
        
        // TODO: Load the database and navigate to quiz screen
        // This is where you'd implement the quiz loading logic
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
