package com.medqb.app.shared.data.database

/**
 * Question attempt statuses corresponding to session selection criteria:
 * - NOT_YET_ANSWERED: Questions with 0 attempts in the database.
 * - ANSWERED_INCORRECTLY: Questions answered incorrectly.
 * - ANSWERED_CORRECTLY: Questions answered correctly.
 */
enum class QuestionStatus(
    val displayName: String,
    val description: String
) {
    NOT_YET_ANSWERED(
        displayName = "Not yet answered",
        description = "Questions you haven't attempted yet"
    ),
    ANSWERED_INCORRECTLY(
        displayName = "Answered incorrectly",
        description = "Questions answered with an incorrect choice"
    ),
    ANSWERED_CORRECTLY(
        displayName = "Answered correctly",
        description = "Questions answered correctly"
    )
}

/**
 * How status filters should be evaluated across user attempt history:
 * - LATEST_ATTEMPT: Based on most recent question attempt.
 * - ALL_ATTEMPTS: Based on all previous question attempts.
 */
enum class StatusScope(
    val displayName: String,
    val shortLabel: String
) {
    LATEST_ATTEMPT(
        displayName = "Based on most recent question attempt",
        shortLabel = "Most recent"
    ),
    ALL_ATTEMPTS(
        displayName = "Based on all previous question attempts",
        shortLabel = "All attempts"
    )
}

/**
 * Composite question status filter.
 * @param statuses The set of [QuestionStatus] criteria to include (multi-selective). When empty, all questions are included.
 * @param scope How the statuses are evaluated across attempts (mono-selective).
 */
data class StatusFilter(
    val statuses: Set<QuestionStatus> = emptySet(),
    val scope: StatusScope = StatusScope.ALL_ATTEMPTS
) {
    val isAll: Boolean
        get() = statuses.isEmpty() || statuses.size == QuestionStatus.entries.size

    val isEmpty: Boolean
        get() = statuses.isEmpty()

    fun serialize(): String = "${statuses.joinToString(",") { it.name }}:${scope.name}"

    companion object {
        val ALL = StatusFilter(
            statuses = emptySet(),
            scope = StatusScope.ALL_ATTEMPTS
        )

        val DEFAULT = ALL

        fun deserialize(raw: String): StatusFilter {
            if (raw.isBlank()) return DEFAULT
            val parts = raw.split(":")
            val statuses = parts[0].split(",")
                .mapNotNull { token ->
                    token.trim().takeIf { it.isNotEmpty() }?.let { t ->
                        runCatching { QuestionStatus.valueOf(t) }.getOrNull()
                    }
                }
                .toSet()
            val scope = parts.getOrNull(1)?.let { token ->
                runCatching { StatusScope.valueOf(token.trim()) }.getOrNull()
            } ?: StatusScope.ALL_ATTEMPTS

            return StatusFilter(statuses = statuses, scope = scope)
        }
    }
}
