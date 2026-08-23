package com.medqb.app.shared.data

import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed [TextHighlightsRepository].
 *
 * Stateless by design — all operations are addressed by `(dbName, questionId)` and
 * there is no in-memory cache or "current question" to invalidate, so stale data can
 * never leak across question switches.
 *
 * Concurrency model: mutations run as serialized read-modify-write sequences under
 * [mutationMutex] (overlap detection reads the latest persisted state, writes the
 * merged highlight, then re-reads for the return value). Because the refresh happens
 * inside the same critical section as the write, the last result a caller receives is
 * always the freshest one.
 */
@Inject
@SingleIn(AppScope::class)
class DefaultTextHighlightsRepository(
    private val userDataManager: UserDataManager,
) : TextHighlightsRepository {

    private val mutationMutex = Mutex()

    override suspend fun getHighlightsForQuestion(
        dbName: String,
        questionId: Long,
    ): List<TextHighlight> {
        if (dbName.isEmpty()) return emptyList()
        return userDataManager.getAllTextHighlightsForQuestion(dbName, questionId)
    }

    override suspend fun addHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor,
    ): List<TextHighlight> {
        if (dbName.isEmpty()) return emptyList()
        val normalizedStart = minOf(startOffset, endOffset)
        val normalizedEnd = maxOf(startOffset, endOffset)
        if (normalizedStart >= normalizedEnd) {
            return getHighlightsForQuestion(dbName, questionId)
        }

        mutationMutex.withLock {
            val sectionHighlights = userDataManager
                .getAllTextHighlightsForQuestion(dbName, questionId)
                .filter { it.section == section }
            val overlappingHighlights = sectionHighlights.filter {
                it.overlapsStrictly(normalizedStart, normalizedEnd)
            }

            // Tapping an existing highlight with the identical range+color is a no-op.
            val exactSameHighlight = overlappingHighlights.singleOrNull {
                it.startOffset == normalizedStart &&
                    it.endOffset == normalizedEnd &&
                    it.color == color
            }
            if (exactSameHighlight != null && overlappingHighlights.size == 1) {
                return userDataManager.getAllTextHighlightsForQuestion(dbName, questionId)
            }

            val mergedStart = minOf(
                normalizedStart,
                overlappingHighlights.minOfOrNull { it.startOffset } ?: normalizedStart
            )
            val mergedEnd = maxOf(
                normalizedEnd,
                overlappingHighlights.maxOfOrNull { it.endOffset } ?: normalizedEnd
            )
            val mergedHighlightedText = mergeHighlightedText(
                mergedStart = mergedStart,
                mergedEnd = mergedEnd,
                normalizedStart = normalizedStart,
                normalizedEnd = normalizedEnd,
                highlightedText = highlightedText,
                overlappingHighlights = overlappingHighlights
            )

            userDataManager.replaceTextHighlightsWithMerged(
                dbName = dbName,
                questionId = questionId,
                section = section,
                removeHighlightIds = overlappingHighlights.map { it.id },
                startOffset = mergedStart,
                endOffset = mergedEnd,
                highlightedText = mergedHighlightedText,
                color = color
            )

            return userDataManager.getAllTextHighlightsForQuestion(dbName, questionId)
        }
    }

    override suspend fun removeHighlight(
        dbName: String,
        questionId: Long,
        highlightId: Long,
    ): List<TextHighlight> {
        mutationMutex.withLock {
            userDataManager.removeTextHighlight(highlightId)
            return userDataManager.getAllTextHighlightsForQuestion(dbName, questionId)
        }
    }

    override suspend fun updateHighlightColor(
        dbName: String,
        questionId: Long,
        highlightId: Long,
        color: HighlightColor,
    ): List<TextHighlight> {
        mutationMutex.withLock {
            userDataManager.updateTextHighlightColor(highlightId, color)
            return userDataManager.getAllTextHighlightsForQuestion(dbName, questionId)
        }
    }

    private fun mergeHighlightedText(
        mergedStart: Int,
        mergedEnd: Int,
        normalizedStart: Int,
        normalizedEnd: Int,
        highlightedText: String,
        overlappingHighlights: List<TextHighlight>
    ): String {
        data class TextSegment(val startOffset: Int, val endOffset: Int, val text: String)

        val totalLength = mergedEnd - mergedStart
        if (totalLength <= 0) return ""

        val mergedChars = CharArray(totalLength) { '\u0000' }
        val segments = buildList {
            overlappingHighlights.forEach {
                add(TextSegment(it.startOffset, it.endOffset, it.highlightedText))
            }
            add(TextSegment(normalizedStart, normalizedEnd, highlightedText))
        }

        segments.forEach { segment ->
            val segmentStart = segment.startOffset.coerceAtLeast(mergedStart)
            val segmentEnd = segment.endOffset.coerceAtMost(mergedEnd)
            if (segmentStart >= segmentEnd) return@forEach

            val sourceOffset = segmentStart - segment.startOffset
            val maxWrite = minOf(segmentEnd - segmentStart, segment.text.length - sourceOffset)
            if (maxWrite <= 0) return@forEach

            for (i in 0 until maxWrite) {
                mergedChars[(segmentStart - mergedStart) + i] = segment.text[sourceOffset + i]
            }
        }

        val hasGap = mergedChars.any { it == '\u0000' }
        if (hasGap) {
            return if (normalizedStart == mergedStart && normalizedEnd == mergedEnd) {
                highlightedText
            } else {
                (overlappingHighlights + TextHighlight(
                    dbName = "",
                    questionId = -1,
                    section = HighlightSection.QUESTION,
                    startOffset = normalizedStart,
                    endOffset = normalizedEnd,
                    highlightedText = highlightedText,
                    color = HighlightColor.YELLOW,
                    createdAt = 0L
                ))
                    .sortedBy { it.startOffset }
                    .joinToString(separator = "") { it.highlightedText }
            }
        }

        return mergedChars.concatToString()
    }

    private fun TextHighlight.overlapsStrictly(start: Int, end: Int): Boolean {
        return startOffset < end && endOffset > start
    }
}
