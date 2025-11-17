package com.medicalquiz.app.ui

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.ui.theme.MedicalQuizTheme

/**
 * Handler for filter dialogs (Subject and System)
 * Uses native Compose dialogs for proper lifecycle management
 */
class FilterDialogHandler(
    private val activity: AppCompatActivity
) {
    private val context: Context = activity
    
    // Database manager operations are provided by the ViewModel now
    
    fun showSubjectSelectionDialog(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
        , onApply: (() -> Unit)? = null
    ) {
        if (subjects.isEmpty()) {
            showNoDataDialog("No subjects found")
            return
        }
        showSubjectSelectionDialogInternal(subjects, currentSubjectIds, viewModel, onApply)
    }

    /**
     * Variant of the subject selection dialog that sets the selected subjects
     * silently (does not call applySelectedSubjects), so callers can stage
     * changes and later apply them when the user confirms in a separate UI.
     */
    fun showSubjectSelectionDialogSilently(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
        onApply: (() -> Unit)? = null
    ) {
        if (subjects.isEmpty()) {
            showNoDataDialog("No subjects found")
            return
        }
        showSelectionDialog(
            title = "Select Subjects",
            items = subjects,
            isChecked = { currentSubjectIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected ->
                viewModel.setSelectedSubjectsSilently(selected)
                onApply?.let { it.invoke() }
            }
        )
    }
    
    fun showSystemSelectionDialog(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
        , onApply: (() -> Unit)? = null
    ) {
        if (systems.isEmpty()) {
            showNoDataDialog("No systems found")
            return
        }
        showSystemSelectionDialogInternal(systems, currentSystemIds, viewModel, onApply)
    }

    fun showSystemSelectionDialogSilently(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
        onApply: (() -> Unit)? = null
    ) {
        if (systems.isEmpty()) {
            showNoDataDialog("No systems found")
            return
        }
        showSelectionDialog(
            title = "Select Systems",
            items = systems,
            isChecked = { currentSystemIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected ->
                viewModel.setSelectedSystemsSilently(selected)
                onApply?.let { it.invoke() }
            }
        )
    }
    
    private fun showSubjectSelectionDialogInternal(
        subjects: List<Subject>,
        currentSubjectIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
        , onApply: (() -> Unit)? = null
    ) {
        showSelectionDialog(
            title = "Select Subjects",
            items = subjects,
            isChecked = { currentSubjectIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected -> 
                viewModel.applySelectedSubjects(selected)
                onApply?.let { it.invoke() }
            }
        )
    }
    
    private fun showSystemSelectionDialogInternal(
        systems: List<System>,
        currentSystemIds: Set<Long>,
        viewModel: com.medicalquiz.app.viewmodel.QuizViewModel
        , onApply: (() -> Unit)? = null
    ) {
        showSelectionDialog(
            title = "Select Systems",
            items = systems,
            isChecked = { currentSystemIds.contains(it.id) },
            labelProvider = { it.name },
            idProvider = { it.id },
            onApply = { selected -> 
                viewModel.applySelectedSystems(selected)
                onApply?.let { it.invoke() }
            }
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
        // Create a ComposeView with proper lifecycle owner setup
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
        }

        val dialog = Dialog(context).apply {
            setContentView(composeView)
        }

        composeView.setContent {
            MedicalQuizTheme {
                GenericSelectionMenuDialog(
                    title = title,
                    items = items,
                    selectedIds = items.mapNotNull { item -> 
                        idProvider(item).takeIf { isChecked(item) } 
                    }.toSet(),
                    labelProvider = labelProvider,
                    idProvider = idProvider,
                    onApply = { selected ->
                        onApply(selected)
                        dialog.dismiss()
                    },
                    onCancel = { dialog.dismiss() },
                    onClear = {
                        onApply(emptySet())
                        dialog.dismiss()
                    },
                    showSelectAll = true
                )
            }
        }

        dialog.show()
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
