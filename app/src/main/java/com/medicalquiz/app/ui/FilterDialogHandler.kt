package com.medicalquiz.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.utils.launchCatching

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
        lifecycleScope.launchCatching(
            block = { databaseManager.getSubjects() },
            onSuccess = { subjects ->
                if (subjects.isEmpty()) {
                    showNoDataDialog("No subjects found")
                } else {
                    showSubjectSelectionDialog(subjects, currentSubjectIds, onSubjectsSelected)
                }
            },
            onFailure = { throwable ->
                showErrorDialog("Error loading subjects: ${throwable.message}")
            }
        )
    }
    
    fun showSystemFilterDialog(
        currentSystemIds: Set<Long>,
        currentSubjectIds: Set<Long>,
        onSystemsSelected: (Set<Long>) -> Unit
    ) {
        lifecycleScope.launchCatching(
            block = {
                databaseManager.getSystems(
                    currentSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                )
            },
            onSuccess = { systems ->
                if (systems.isEmpty()) {
                    showNoDataDialog("No systems found")
                } else {
                    showSystemSelectionDialog(systems, currentSystemIds, onSystemsSelected)
                }
            },
            onFailure = { throwable ->
                showErrorDialog("Error loading systems: ${throwable.message}")
            }
        )
    }
    
    private fun showSubjectSelectionDialog(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        onSubjectsSelected: (Set<Long>) -> Unit
    ) {
        showSelectionDialog(
            title = "Select Subjects",
            items = subjects,
            isChecked = { currentSubjectIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = onSubjectsSelected
        )
    }
    
    private fun showSystemSelectionDialog(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        onSystemsSelected: (Set<Long>) -> Unit
    ) {
        showSelectionDialog(
            title = "Select Systems",
            items = systems,
            isChecked = { currentSystemIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = onSystemsSelected
        )
    }
    
    private fun <T> showSelectionDialog(
        title: String,
        items: List<T>,
        isChecked: (T) -> Boolean,
        labelProvider: (T) -> String,
        idProvider: (T) -> Long,
        onApply: (Set<Long>) -> Unit
    ) {
        val labels = items.map(labelProvider).toTypedArray()
        val checkedItems = BooleanArray(items.size) { index -> isChecked(items[index]) }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, checked ->
                checkedItems[which] = checked
            }
            .setPositiveButton("Apply") { _, _ ->
                val selected = items.mapIndexedNotNull { index, item ->
                    idProvider(item).takeIf { checkedItems[index] }
                }.toSet()
                onApply(selected)
            }
            .setNeutralButton("Clear") { _, _ -> onApply(emptySet()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNoDataDialog(message: String) = showMessageDialog(message = message)

    private fun showErrorDialog(message: String) = showMessageDialog(title = "Error", message = message)

    private fun showMessageDialog(title: String? = null, message: String) {
        AlertDialog.Builder(context).apply {
            title?.let { setTitle(it) }
            setMessage(message)
            setPositiveButton("OK", null)
        }.show()
    }
}
