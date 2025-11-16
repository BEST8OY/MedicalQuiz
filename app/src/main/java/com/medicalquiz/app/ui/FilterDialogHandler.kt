package com.medicalquiz.app.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
// no longer needed — handler is UI-only
// ViewModel not used here — UI-only handler
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
// no longer uses ViewModel or Resource; Activity handles fetching

/**
 * Handler for filter dialogs (Subject and System)
 */
class FilterDialogHandler(
    private val context: Context
) {
    
    // Database manager operations are provided by the ViewModel now
    
    fun showSubjectSelectionDialog(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
    ) {
        if (subjects.isEmpty()) {
            showNoDataDialog("No subjects found")
            return
        }
        showSubjectSelectionDialogInternal(subjects, currentSubjectIds, viewModel)
    }
    
    fun showSystemSelectionDialog(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
    ) {
        if (systems.isEmpty()) {
            showNoDataDialog("No systems found")
            return
        }
        showSystemSelectionDialogInternal(systems, currentSystemIds, viewModel)
    }
    
    private fun showSubjectSelectionDialogInternal(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
    ) {
        showSelectionDialog(
            title = "Select Subjects",
            items = subjects,
            isChecked = { currentSubjectIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected -> viewModel.applySelectedSubjects(selected) }
        )
    }
    
    private fun showSystemSelectionDialogInternal(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
    ) {
        showSelectionDialog(
            title = "Select Systems",
            items = systems,
            isChecked = { currentSystemIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected -> viewModel.applySelectedSystems(selected) }
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
