package com.medqb.app.shared.data

import com.medqb.app.shared.data.local.entity.TextHighlightEntity
import com.medqb.app.shared.data.local.entity.toDomain
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

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

    private suspend fun getAllHighlights(dbName: String, questionId: Long): List<TextHighlight> {
        val canonicalDbName = dbName.trim().removeSuffix(".db").trim()
        if (canonicalDbName.isBlank()) return emptyList()
        return userDataManager.textHighlightDao()
            .getAllForQuestion(canonicalDbName, questionId)
            .map { it.toDomain() }
    }

    override suspend fun getHighlightsForQuestion(
        dbName: String,
        questionId: Long,
    ): List<TextHighlight> {
        val canonicalDbName = dbName.trim().removeSuffix(".db").trim()
        if (canonicalDbName.isBlank()) return emptyList()
        return getAllHighlights(canonicalDbName, questionId)
    }

    override suspend fun addHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        blockKey: String,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor,
    ): List<TextHighlight> {
        val canonicalDbName = dbName.trim().removeSuffix(".db").trim()
        if (canonicalDbName.isBlank()) return emptyList()
        val normalizedStart = minOf(startOffset, endOffset)
        val normalizedEnd = maxOf(startOffset, endOffset)
        if (normalizedStart >= normalizedEnd) {
            return getHighlightsForQuestion(canonicalDbName, questionId)
        }

        mutationMutex.withLock {
            val allHighlights = getAllHighlights(canonicalDbName, questionId)
            val blockHighlights = allHighlights.filter {
                it.section == section && it.blockKey == blockKey
            }
            val overlappingHighlights = blockHighlights.filter {
                it.overlaps(normalizedStart, normalizedEnd)
            }

            // Tapping an existing highlight with the identical range+color is a no-op.
            val exactSameHighlight = overlappingHighlights.singleOrNull {
                it.startOffset == normalizedStart &&
                    it.endOffset == normalizedEnd &&
                    it.color == color
            }
            if (exactSameHighlight != null && overlappingHighlights.size == 1) {
                return allHighlights
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

            val now = Clock.System.now().toEpochMilliseconds()
            val insertEntity = TextHighlightEntity(
                dbName = canonicalDbName,
                questionId = questionId,
                section = section.name,
                blockKey = blockKey,
                startOffset = mergedStart,
                endOffset = mergedEnd,
                highlightedText = mergedHighlightedText,
                color = color.name,
                createdAt = now
            )
            userDataManager.textHighlightDao().replaceWithMerged(
                removeIds = overlappingHighlights.map { it.id },
                insert = insertEntity
            )

            return getAllHighlights(canonicalDbName, questionId)
        }
    }

    override suspend fun removeHighlight(
        dbName: String,
        questionId: Long,
        highlightId: Long,
    ): List<TextHighlight> {
        val canonicalDbName = dbName.trim().removeSuffix(".db").trim()
        if (canonicalDbName.isBlank()) return emptyList()
        mutationMutex.withLock {
            userDataManager.textHighlightDao().deleteById(highlightId)
            return getAllHighlights(canonicalDbName, questionId)
        }
    }

    override suspend fun updateHighlightColor(
        dbName: String,
        questionId: Long,
        highlightId: Long,
        color: HighlightColor,
    ): List<TextHighlight> {
        val canonicalDbName = dbName.trim().removeSuffix(".db").trim()
        if (canonicalDbName.isBlank()) return emptyList()
        mutationMutex.withLock {
            userDataManager.textHighlightDao().updateColor(highlightId, color.name)
            return getAllHighlights(canonicalDbName, questionId)
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
                buildString(mergedChars.size) {
                    for (c in mergedChars) {
                        append(if (c != '\u0000') c else ' ')
                    }
                }
            }
        }

        return mergedChars.concatToString()
    }
}
