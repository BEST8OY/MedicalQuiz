package com.medqb.app.shared.ui.screens.filter

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope

internal fun formatSelectionSubtitle(
    selectedCount: Int,
    excludedCount: Int,
    singular: String,
    plural: String,
): String {
    return when {
        selectedCount == 0 && excludedCount == 0 -> "All $plural"
        selectedCount > 0 && excludedCount == 0 -> if (selectedCount == 1) "1 $singular selected" else "$selectedCount selected"
        selectedCount == 0 && excludedCount > 0 -> if (excludedCount == 1) "All except 1 $singular" else "All except $excludedCount"
        else -> "$selectedCount selected \u2022 $excludedCount excluded"
    }
}

internal fun formatDifficultyLabel(selectedTiers: Set<DifficultyTier>): String {
    if (selectedTiers.isEmpty() || selectedTiers.size == DifficultyTier.entries.size) {
        return "All Difficulties"
    }
    if (selectedTiers.size > 2) {
        return "${selectedTiers.size} selected"
    }
    val sorted = selectedTiers.sortedBy { it.ordinal }
    return sorted.joinToString(", ") { it.displayName }
}

internal fun formatStatusFilterLabel(filter: StatusFilter): String {
    if (filter.isAll) return "All Questions"

    if (filter.statuses.size == 1) {
        val single = filter.statuses.first()
        return when (single) {
            QuestionStatus.NOT_YET_ANSWERED -> "Not yet answered"
            QuestionStatus.ANSWERED_INCORRECTLY -> if (filter.scope == StatusScope.LATEST_ATTEMPT) "Last incorrect" else "Ever incorrect"
            QuestionStatus.ANSWERED_CORRECTLY -> if (filter.scope == StatusScope.LATEST_ATTEMPT) "Last correct" else "Ever correct"
            QuestionStatus.MARKED -> "Marked"
        }
    }

    if (filter.statuses == setOf(QuestionStatus.NOT_YET_ANSWERED, QuestionStatus.ANSWERED_INCORRECTLY)) {
        return if (filter.scope == StatusScope.LATEST_ATTEMPT) {
            "Unanswered & Incorrect"
        } else {
            "Unanswered & Ever incorrect"
        }
    }

    if (filter.statuses == setOf(QuestionStatus.NOT_YET_ANSWERED, QuestionStatus.MARKED)) {
        return "Unanswered & Marked"
    }

    if (filter.statuses == setOf(QuestionStatus.ANSWERED_INCORRECTLY, QuestionStatus.MARKED)) {
        return if (filter.scope == StatusScope.LATEST_ATTEMPT) {
            "Incorrect & Marked"
        } else {
            "Ever incorrect & Marked"
        }
    }

    return if (filter.usesAttemptScope) {
        "${filter.statuses.size} statuses (${filter.scope.shortLabel})"
    } else {
        "${filter.statuses.size} statuses"
    }
}
