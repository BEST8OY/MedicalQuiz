package com.medqb.app.shared.data.database

/**
 * 5-tier difficulty system based on global empirical question correct rates:
 * - [VERY_EASY]: Easiest 20% of all questions (top 80% - 100% correct rate).
 * - [EASY]: More difficult than bottom 20% but easier than upper 50% (50% - 80% correct rate).
 * - [INTERMEDIATE]: More difficult than lower 50% but easier than upper 20% (20% - 50% correct rate).
 * - [DIFFICULT]: More difficult than lower 80% but easier than top 5% (5% - 20% correct rate).
 * - [VERY_DIFFICULT]: The most difficult 5% of all questions (0% - 5% correct rate).
 */
enum class DifficultyTier(
    val displayName: String,
    val description: String,
) {
    VERY_EASY(
        displayName = "Very Easy",
        description = "Easiest 20% of questions",
    ),
    EASY(
        displayName = "Easy",
        description = "20% – 50% difficulty",
    ),
    INTERMEDIATE(
        displayName = "Intermediate",
        description = "50% – 80% difficulty",
    ),
    DIFFICULT(
        displayName = "Difficult",
        description = "80% – 95% difficulty",
    ),
    VERY_DIFFICULT(
        displayName = "Very Difficult",
        description = "Top 5% most difficult",
    );
}
