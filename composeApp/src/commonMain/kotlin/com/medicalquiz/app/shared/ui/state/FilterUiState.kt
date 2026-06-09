package com.medicalquiz.app.shared.ui.state

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.SubmissionMode
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.utils.Resource

/**
 * UI State for the Filter hub screen.
 */
data class FilterUiState(
    val databaseName: String = "",
    val selectedSubjectIds: Set<Long> = emptySet(),
    val selectedSystemIds: Set<Long> = emptySet(),
    val performanceFilter: PerformanceFilter = PerformanceFilter.ALL,
    val subjectsResource: Resource<List<Subject>> = Resource.Success(emptyList()),
    val systemsResource: Resource<List<System>> = Resource.Success(emptyList()),
    val previewQuestionCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val submissionMode: SubmissionMode = SubmissionMode.INSTANT,
) {
    companion object {
        val EMPTY = FilterUiState()
    }
}
