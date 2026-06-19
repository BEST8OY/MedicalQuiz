package com.medqb.app.shared.data

import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.platform.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for managing text highlights within question content.
 * Maintains an in-memory cache of highlights for the current question
 * and syncs with UserDataManager for persistence.
 */
class TextHighlightsRepository(
    private val userDataManager: UserDataManager,
    private val scope: CoroutineScope
) {
    private data class HighlightContext(
        val dbName: String,
        val questionId: Long
    )

    private var currentDbName: String = ""
    private var currentQuestionId: Long = -1
    private var activeLoadRequestId: Long = 0
    private val highlightMutationMutex = Mutex()

    // Highlights for current question, grouped by section
    private val _questionHighlights = MutableStateFlow<List<TextHighlight>>(emptyList())
    val questionHighlights: StateFlow<List<TextHighlight>> = _questionHighlights.asStateFlow()

    private val _explanationHighlights = MutableStateFlow<List<TextHighlight>>(emptyList())
    val explanationHighlights: StateFlow<List<TextHighlight>> = _explanationHighlights.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Load highlights for a specific question.
     * Call this when navigating to a new question.
     */
    fun loadHighlightsForQuestion(dbName: String, questionId: Long) {
        if (dbName.isEmpty()) return
        currentDbName = dbName
        currentQuestionId = questionId
        val context = currentContextSnapshot() ?: return
        val requestId = ++activeLoadRequestId

        scope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val allHighlights = userDataManager.getAllTextHighlightsForQuestion(
                    context.dbName,
                    context.questionId
                )

                if (!isRequestActive(requestId, context)) return@launch

                _questionHighlights.value = allHighlights.filter {
                    it.section == HighlightSection.QUESTION
                }
                _explanationHighlights.value = allHighlights.filter {
                    it.section == HighlightSection.EXPLANATION
                }
            } catch (e: Exception) {
                if (isRequestActive(requestId, context)) {
                    Logger.e("TextHighlightsRepository", "Error loading text highlights", e)
                    clearCachedHighlights()
                }
            } finally {
                if (requestId == activeLoadRequestId) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Get highlights for a specific section (from cache).
     */
    fun getHighlightsForSection(section: HighlightSection): List<TextHighlight> {
        return when (section) {
            HighlightSection.QUESTION -> _questionHighlights.value
            HighlightSection.EXPLANATION -> _explanationHighlights.value
        }
    }

    /**
     * Add a new text highlight.
     */
    fun addHighlight(
        section: HighlightSection,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor = HighlightColor.YELLOW
    ) {
        val context = currentContextSnapshot() ?: return
        val normalizedStart = minOf(startOffset, endOffset)
        val normalizedEnd = maxOf(startOffset, endOffset)
        if (normalizedStart >= normalizedEnd) return

        scope.launch(Dispatchers.IO) {
            try {
                highlightMutationMutex.withLock {
                    if (!matchesCurrentContext(context)) return@withLock

                    val latestSectionHighlights = getHighlightsForSection(section)
                    val overlappingHighlights = latestSectionHighlights.filter {
                        it.overlapsStrictly(normalizedStart, normalizedEnd)
                    }

                    val exactSameHighlight = overlappingHighlights.singleOrNull {
                        it.startOffset == normalizedStart &&
                            it.endOffset == normalizedEnd &&
                            it.color == color
                    }
                    if (exactSameHighlight != null && overlappingHighlights.size == 1) return@withLock

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

                    val highlight = userDataManager.replaceTextHighlightsWithMerged(
                        dbName = context.dbName,
                        questionId = context.questionId,
                        section = section,
                        removeHighlightIds = overlappingHighlights.map { it.id },
                        startOffset = mergedStart,
                        endOffset = mergedEnd,
                        highlightedText = mergedHighlightedText,
                        color = color
                    )

                    if (!matchesCurrentContext(context)) return@withLock

                    val overlapIds = overlappingHighlights.map { it.id }.toSet()
                    val mergedInMemory = when (section) {
                        HighlightSection.QUESTION -> _questionHighlights.value
                        HighlightSection.EXPLANATION -> _explanationHighlights.value
                    }.filterNot { it.id in overlapIds } + highlight

                    when (section) {
                        HighlightSection.QUESTION -> {
                            _questionHighlights.value = mergedInMemory
                                .sortedBy { it.startOffset }
                        }

                        HighlightSection.EXPLANATION -> {
                            _explanationHighlights.value = mergedInMemory
                                .sortedBy { it.startOffset }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("TextHighlightsRepository", "Error adding text highlight", e)
            }
        }
    }

    /**
     * Remove a text highlight by ID.
     */
    fun removeHighlight(highlightId: Long) {
        val context = currentContextSnapshot()

        scope.launch(Dispatchers.IO) {
            try {
                highlightMutationMutex.withLock {
                    userDataManager.removeTextHighlight(highlightId)

                    if (context != null && !matchesCurrentContext(context)) return@withLock

                    _questionHighlights.value = _questionHighlights.value.filter { it.id != highlightId }
                    _explanationHighlights.value = _explanationHighlights.value.filter { it.id != highlightId }
                }
            } catch (e: Exception) {
                Logger.e("TextHighlightsRepository", "Error removing text highlight", e)
            }
        }
    }

    /**
     * Update the color of a text highlight.
     */
    fun updateHighlightColor(highlightId: Long, color: HighlightColor) {
        val context = currentContextSnapshot()

        scope.launch(Dispatchers.IO) {
            try {
                highlightMutationMutex.withLock {
                    userDataManager.updateTextHighlightColor(highlightId, color)

                    if (context != null && !matchesCurrentContext(context)) return@withLock

                    _questionHighlights.value = _questionHighlights.value.map {
                        if (it.id == highlightId) it.copy(color = color) else it
                    }
                    _explanationHighlights.value = _explanationHighlights.value.map {
                        if (it.id == highlightId) it.copy(color = color) else it
                    }
                }
            } catch (e: Exception) {
                Logger.e("TextHighlightsRepository", "Error updating highlight color", e)
            }
        }
    }

    /**
     * Find a highlight at a specific character offset.
     */
    fun findHighlightAtOffset(section: HighlightSection, offset: Int): TextHighlight? {
        val highlights = getHighlightsForSection(section)
        return highlights.find { it.contains(offset) }
    }

    /**
     * Check if a range overlaps with existing highlights.
     */
    fun hasOverlappingHighlight(section: HighlightSection, start: Int, end: Int): Boolean {
        val highlights = getHighlightsForSection(section)
        return highlights.any { it.overlaps(start, end) }
    }

    /**
     * Clear all highlights for the current question.
     */
    fun clearAllHighlightsForCurrentQuestion() {
        val context = currentContextSnapshot() ?: return

        scope.launch(Dispatchers.IO) {
            try {
                highlightMutationMutex.withLock {
                    userDataManager.clearTextHighlightsForQuestion(context.dbName, context.questionId)
                    if (!matchesCurrentContext(context)) return@withLock
                    clearCachedHighlights()
                }
            } catch (e: Exception) {
                Logger.e("TextHighlightsRepository", "Error clearing highlights", e)
            }
        }
    }

    /**
     * Clear highlights for a specific section of the current question.
     */
    fun clearHighlightsForSection(section: HighlightSection) {
        val context = currentContextSnapshot() ?: return

        scope.launch(Dispatchers.IO) {
            try {
                highlightMutationMutex.withLock {
                    userDataManager.clearTextHighlightsForQuestion(
                        context.dbName,
                        context.questionId,
                        section
                    )

                    if (!matchesCurrentContext(context)) return@withLock

                    when (section) {
                        HighlightSection.QUESTION -> _questionHighlights.value = emptyList()
                        HighlightSection.EXPLANATION -> _explanationHighlights.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Logger.e("TextHighlightsRepository", "Error clearing section highlights", e)
            }
        }
    }

    /**
     * Get the current question ID being tracked.
     */
    fun getCurrentQuestionId(): Long = currentQuestionId

    private fun currentContextSnapshot(): HighlightContext? {
        if (currentDbName.isEmpty() || currentQuestionId < 0) return null
        return HighlightContext(currentDbName, currentQuestionId)
    }

    private fun matchesCurrentContext(context: HighlightContext): Boolean {
        return context.dbName == currentDbName && context.questionId == currentQuestionId
    }

    private fun isRequestActive(requestId: Long, context: HighlightContext): Boolean {
        return requestId == activeLoadRequestId && matchesCurrentContext(context)
    }

    private fun clearCachedHighlights() {
        _questionHighlights.value = emptyList()
        _explanationHighlights.value = emptyList()
    }

    private fun invalidatePendingLoads() {
        activeLoadRequestId++
    }

    private data class TextSegment(
        val startOffset: Int,
        val endOffset: Int,
        val text: String
    )

    private fun mergeHighlightedText(
        mergedStart: Int,
        mergedEnd: Int,
        normalizedStart: Int,
        normalizedEnd: Int,
        highlightedText: String,
        overlappingHighlights: List<TextHighlight>
    ): String {
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
