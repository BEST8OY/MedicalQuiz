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
    private var databaseManager: DatabaseManager
) {
    
    fun updateDatabaseManager(newManager: DatabaseManager) {
        databaseManager = newManager
    }
    
    fun showSubjectFilterDialog(
        currentSubjectIds: Set<Long>,
        onSubjectsSelected: (Set<Long>) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val subjects = databaseManager.getSubjects()
                if (subjects.isEmpty()) {
                    showNoDataDialog("No subjects found")
                    return@launch
                }
                
                showSubjectSelectionDialog(subjects, currentSubjectIds, onSubjectsSelected)
            } catch (e: Exception) {
                showErrorDialog("Error loading subjects: ${e.message}")
            }
        }
    }
    
    fun showSystemFilterDialog(
        currentSystemIds: Set<Long>,
        currentSubjectIds: Set<Long>,
        onSystemsSelected: (Set<Long>) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val systems = databaseManager.getSystems(
                    currentSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                )
                
                if (systems.isEmpty()) {
                    showNoDataDialog("No systems found")
                    return@launch
                }
                
                showSystemSelectionDialog(systems, currentSystemIds, onSystemsSelected)
            } catch (e: Exception) {
                showErrorDialog("Error loading systems: ${e.message}")
            }
        }
    }
    
    private fun showSubjectSelectionDialog(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        onSubjectsSelected: (Set<Long>) -> Unit
    ) {
        val items = subjects.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(subjects.size) { index ->
            currentSubjectIds.contains(subjects[index].id)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Select Subjects")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val selected = subjects.mapIndexedNotNull { index, subject ->
                    subject.id.takeIf { checkedItems[index] }
                }.toSet()
                onSubjectsSelected(selected)
            }
            .setNeutralButton("Clear") { _, _ ->
                onSubjectsSelected(emptySet())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSystemSelectionDialog(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        onSystemsSelected: (Set<Long>) -> Unit
    ) {
        val items = systems.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(systems.size) { index ->
            currentSystemIds.contains(systems[index].id)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Select Systems")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val selected = systems.mapIndexedNotNull { index, system ->
                    system.id.takeIf { checkedItems[index] }
                }.toSet()
                onSystemsSelected(selected)
            }
            .setNeutralButton("Clear") { _, _ ->
                onSystemsSelected(emptySet())
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
