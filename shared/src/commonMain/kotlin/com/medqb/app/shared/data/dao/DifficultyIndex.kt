package com.medqb.app.shared.data.dao

import com.medqb.app.shared.data.database.DifficultyTier
import kotlin.math.roundToInt

class DifficultyIndex private constructor(
    private val tierByQid: Map<Long, DifficultyTier>,
    val tierCounts: Map<DifficultyTier, Int>,
    val isAvailable: Boolean,
) {
    fun getTier(qid: Long): DifficultyTier? = tierByQid[qid]

    fun matches(qid: Long, selectedTiers: Set<DifficultyTier>): Boolean {
        if (!isAvailable || selectedTiers.isEmpty() || selectedTiers.size == DifficultyTier.entries.size) {
            return true
        }
        val tier = tierByQid[qid] ?: return false
        return tier in selectedTiers
    }

    companion object {
        val EMPTY = DifficultyIndex(
            tierByQid = emptyMap(),
            tierCounts = emptyMap(),
            isAvailable = false,
        )

        /**
         * Builds a [DifficultyIndex] from a list of question statistics (id to correct rate).
         *
         * Questions are sorted ascending by correct rate (lowest success rate = highest difficulty).
         * Percentile cutoff boundaries:
         * - Lowest 5% correct rate: index 0 until idxP05 -> [DifficultyTier.VERY_DIFFICULT]
         * - Next 15% correct rate: idxP05 until idxP20 -> [DifficultyTier.DIFFICULT]
         * - Next 30% correct rate: idxP20 until idxP50 -> [DifficultyTier.INTERMEDIATE]
         * - Next 30% correct rate: idxP50 until idxP80 -> [DifficultyTier.EASY]
         * - Top 20% highest correct rate: idxP80 until N -> [DifficultyTier.VERY_EASY]
         */
        fun build(stats: List<Pair<Long, Double>>): DifficultyIndex {
            if (stats.size < 5) {
                return EMPTY
            }

            // Check if there is actual variance in the rates (avoid dummy uniform rates like 1.0/100.0)
            val sampleDistinct = stats.take(50).map { it.second }.distinct().size
            if (sampleDistinct <= 1) {
                val fullDistinct = stats.map { it.second }.distinct().size
                if (fullDistinct <= 1) {
                    return EMPTY
                }
            }

            val sorted = stats.sortedBy { it.second }
            val n = sorted.size

            val idxP05 = (n * 0.05).roundToInt().coerceIn(0, n)
            val idxP20 = (n * 0.20).roundToInt().coerceIn(idxP05, n)
            val idxP50 = (n * 0.50).roundToInt().coerceIn(idxP20, n)
            val idxP80 = (n * 0.80).roundToInt().coerceIn(idxP50, n)

            val map = HashMap<Long, DifficultyTier>(n)
            val counts = mutableMapOf<DifficultyTier, Int>()

            for (i in 0 until n) {
                val qid = sorted[i].first
                val tier = when {
                    i < idxP05 -> DifficultyTier.VERY_DIFFICULT
                    i < idxP20 -> DifficultyTier.DIFFICULT
                    i < idxP50 -> DifficultyTier.INTERMEDIATE
                    i < idxP80 -> DifficultyTier.EASY
                    else -> DifficultyTier.VERY_EASY
                }
                map[qid] = tier
                counts[tier] = (counts[tier] ?: 0) + 1
            }

            return DifficultyIndex(
                tierByQid = map,
                tierCounts = counts,
                isAvailable = true,
            )
        }
    }
}
