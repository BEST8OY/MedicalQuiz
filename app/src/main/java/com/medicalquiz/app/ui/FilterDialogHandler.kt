package com.medicalquiz.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.medicalquiz.app.viewmodel.QuizViewModel
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.utils.launchCatching
import com.medicalquiz.app.utils.Resource

/**
 * Handler for filter dialogs (Subject and System)
 */
class FilterDialogHandler(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val viewModel: QuizViewModel
) {
    
    // Database manager operations are provided by the ViewModel now
    
    fun showSubjectFilterDialog(
        currentSubjectIds: Set<Long>,
        onSubjectsSelected: (Set<Long>) -> Unit
    ) {
        lifecycleScope.launchCatching(
            block = { viewModel.getSubjects() },
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
        owner: androidx.lifecycle.LifecycleOwner,
        currentSystemIds: Set<Long>,
        currentSubjectIds: Set<Long>,
        onSystemsSelected: (Set<Long>) -> Unit
    ) {
        // Trigger a fetch in the ViewModel and observe the resulting LiveData
        viewModel.fetchSystemsForSubjects(currentSubjectIds.takeIf { it.isNotEmpty() }?.toList())

        viewModel.systemsResource.observeOnce(owner, androidx.lifecycle.Observer { resource ->
            when (resource) {
                is Resource.Loading -> {
                    // optionally show a loading indicator
                }
                is Resource.Success -> {
                    val systems = resource.data
                    if (systems.isEmpty()) {
                        showNoDataDialog("No systems found")
                    } else {
                        showSystemSelectionDialog(systems, currentSystemIds, onSystemsSelected)
                    }
                }
                is Resource.Error -> {
                    showErrorDialog("Error loading systems: ${resource.message}")
                }
            }
        }
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
