package com.medicalquiz.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
import kotlinx.coroutines.launch

/**
 * Handler for filter dialogs (Subject and System)
 */
class FilterDialogHandler(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val databaseManager: DatabaseManager
) {
    
    fun showSubjectFilterDialog(
        currentSubjectId: Long?,
        onSubjectSelected: (Long?) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val subjects = databaseManager.getSubjects()
                if (subjects.isEmpty()) {
                    showNoDataDialog("No subjects found")
                    return@launch
                }
                
                showSubjectSelectionDialog(subjects, currentSubjectId, onSubjectSelected)
            } catch (e: Exception) {
                showErrorDialog("Error loading subjects: ${e.message}")
            }
        }
    }
    
    fun showSystemFilterDialog(
        currentSystemId: Long?,
        currentSubjectId: Long?,
        onSystemSelected: (Long?) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val systems = databaseManager.getSystems(
                    currentSubjectId?.let { listOf(it) }
                )
                
                if (systems.isEmpty()) {
                    showNoDataDialog("No systems found")
                    return@launch
                }
                
                showSystemSelectionDialog(systems, currentSystemId, onSystemSelected)
            } catch (e: Exception) {
                showErrorDialog("Error loading systems: ${e.message}")
            }
        }
    }
    
    private fun showSubjectSelectionDialog(
        subjects: List<Subject>,
        currentSubjectId: Long?,
        onSubjectSelected: (Long?) -> Unit
    ) {
        val items = arrayOf("All Subjects") + subjects.map { it.name }.toTypedArray()
        val checkedItem = if (currentSubjectId == null) {
            0
        } else {
            subjects.indexOfFirst { it.id == currentSubjectId } + 1
        }
        
        AlertDialog.Builder(context)
            .setTitle("Filter by Subject")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val selectedSubjectId = if (which == 0) null else subjects[which - 1].id
                onSubjectSelected(selectedSubjectId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSystemSelectionDialog(
        systems: List<System>,
        currentSystemId: Long?,
        onSystemSelected: (Long?) -> Unit
    ) {
        val items = arrayOf("All Systems") + systems.map { it.name }.toTypedArray()
        val checkedItem = if (currentSystemId == null) {
            0
        } else {
            systems.indexOfFirst { it.id == currentSystemId } + 1
        }
        
        AlertDialog.Builder(context)
            .setTitle("Filter by System")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val selectedSystemId = if (which == 0) null else systems[which - 1].id
                onSystemSelected(selectedSystemId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showNoDataDialog(message: String) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
