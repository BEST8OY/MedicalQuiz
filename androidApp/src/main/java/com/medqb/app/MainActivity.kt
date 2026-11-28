package com.medqb.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.medqb.app.shared.App
import com.medqb.app.shared.di.LocalAppGraph
import com.medqb.app.shared.platform.AppContext
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasPermission by mutableStateOf(false)
    private var isUserDataReady by mutableStateOf(false)
    private var shouldRetryPermissionCheck = false
    private var hasRequestedInitialPermission = false
    private var permissionGrantJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hasPermission = true
            if (!isUserDataReady) {
                markPermissionGranted()
            }
        } else {
            permissionGrantJob?.cancel()
            permissionGrantJob = null
            hasPermission = false
            isUserDataReady = false
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        shouldRetryPermissionCheck = false
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.init(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        hasRequestedInitialPermission =
            savedInstanceState?.getBoolean(KEY_HAS_REQUESTED_PERMISSION) == true

        val graph = (application as MedQBApp).graph

        // Keep permission status and DB readiness separate so mid-reinit config changes
        // never flash the "Storage Permission Required" screen when permission is already granted.
        hasPermission = hasStoragePermission()
        isUserDataReady = hasPermission && !graph.userDataManager.isReinitializingOrClosed()

        setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                when {
                    !hasPermission -> PermissionScreen(
                        onGrantPermission = { openManageStorageSettings() }
                    )
                    !isUserDataReady -> AppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {}
                    }
                    else -> App()
                }
            }
        }

        checkPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_REQUESTED_PERMISSION, hasRequestedInitialPermission)
    }

    override fun onResume() {
        super.onResume()
        if (shouldRetryPermissionCheck) {
            checkPermissions()
            shouldRetryPermissionCheck = false
        }
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun checkPermissions() {
        val nowGranted = hasStoragePermission()
        val graph = (application as MedQBApp).graph
        hasPermission = nowGranted

        if (nowGranted) {
            if (!isUserDataReady) {
                if (graph.userDataManager.isReinitializingOrClosed()) {
                    ensureUserDataReady()
                } else {
                    markPermissionGranted()
                }
            }
        } else {
            permissionGrantJob?.cancel()
            permissionGrantJob = null
            isUserDataReady = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !hasRequestedInitialPermission) {
                hasRequestedInitialPermission = true
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun markPermissionGranted() {
        if (permissionGrantJob?.isActive == true) return
        val graph = (application as MedQBApp).graph
        permissionGrantJob = lifecycleScope.launch {
            try {
                graph.userDataManager.reinitialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MainActivity", "Failed to reinitialize UserDataManager after permission grant", e)
            }
            val granted = hasStoragePermission()
            hasPermission = granted
            isUserDataReady = granted
        }
    }

    private fun ensureUserDataReady() {
        if (permissionGrantJob?.isActive == true) return
        val graph = (application as MedQBApp).graph
        permissionGrantJob = lifecycleScope.launch {
            try {
                graph.userDataManager.awaitOpen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MainActivity", "Failed to ensure UserDataManager is open", e)
            }
            val granted = hasStoragePermission()
            hasPermission = granted
            isUserDataReady = granted
        }
    }

    private fun openManageStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            shouldRetryPermissionCheck = true
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private companion object {
        const val KEY_HAS_REQUESTED_PERMISSION = "has_requested_initial_permission"
    }
}

@Composable
fun PermissionScreen(onGrantPermission: () -> Unit) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Storage Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "This app needs access to storage to read your quiz databases and media files.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(onClick = onGrantPermission) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
