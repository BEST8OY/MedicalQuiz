package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.HighlightSection
import com.medicalquiz.app.shared.data.models.TextHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // Highlights for current question, grouped by section
    private val _questionHighlights = MutableStateFlow<List<TextHighlight>>(emptyList())
    val questionHighlights: StateFlow<List<TextHighlight>> = _questionHighlights.asStateFlow()

    private val _explanationHighlights = MutableStateFlow<List<TextHighlight>>(emptyList())
    val explanationHighlights: StateFlow<List<TextHighlight>> = _explanationHighlights.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Set the current database name.
     */
    fun setCurrentDatabase(dbName: String) {
        if (dbName == currentDbName) return
        currentDbName = dbName
        currentQuestionId = -1
        clearCachedHighlights()
        invalidatePendingLoads()
        _isLoading.value = false
    }

    /**
     * Load highlights for a specific question.
     * Call this when navigating to a new question.
     */
    fun loadHighlightsForQuestion(questionId: Long) {
        if (currentDbName.isEmpty()) return
        if (questionId == currentQuestionId) return

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
                    println("Error loading text highlights: ${e.message}")
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

        scope.launch(Dispatchers.IO) {
            try {
                val highlight = userDataManager.addTextHighlight(
                    dbName = context.dbName,
                    questionId = context.questionId,
                    section = section,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    highlightedText = highlightedText,
                    color = color
                )

                if (!matchesCurrentContext(context)) return@launch

                when (section) {
                    HighlightSection.QUESTION -> {
                        _questionHighlights.value = (_questionHighlights.value + highlight)
                            .sortedBy { it.startOffset }
                    }

                    HighlightSection.EXPLANATION -> {
                        _explanationHighlights.value = (_explanationHighlights.value + highlight)
                            .sortedBy { it.startOffset }
                    }
                }
            } catch (e: Exception) {
                println("Error adding text highlight: ${e.message}")
            }
        }
    }

    /**
     * Remove a text highlight by ID.
     */
    fun removeHighlight(highlightId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                userDataManager.removeTextHighlight(highlightId)

                _questionHighlights.value = _questionHighlights.value.filter { it.id != highlightId }
                _explanationHighlights.value = _explanationHighlights.value.filter { it.id != highlightId }
            } catch (e: Exception) {
                println("Error removing text highlight: ${e.message}")
            }
        }
    }

    /**
     * Update the color of a text highlight.
     */
    fun updateHighlightColor(highlightId: Long, color: HighlightColor) {
        scope.launch(Dispatchers.IO) {
            try {
                userDataManager.updateTextHighlightColor(highlightId, color)

                _questionHighlights.value = _questionHighlights.value.map {
                    if (it.id == highlightId) it.copy(color = color) else it
                }
                _explanationHighlights.value = _explanationHighlights.value.map {
                    if (it.id == highlightId) it.copy(color = color) else it
                }
            } catch (e: Exception) {
                println("Error updating highlight color: ${e.message}")
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
                userDataManager.clearTextHighlightsForQuestion(context.dbName, context.questionId)
                if (!matchesCurrentContext(context)) return@launch
                clearCachedHighlights()
            } catch (e: Exception) {
                println("Error clearing highlights: ${e.message}")
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
                userDataManager.clearTextHighlightsForQuestion(
                    context.dbName,
                    context.questionId,
                    section
                )

                if (!matchesCurrentContext(context)) return@launch

                when (section) {
                    HighlightSection.QUESTION -> _questionHighlights.value = emptyList()
                    HighlightSection.EXPLANATION -> _explanationHighlights.value = emptyList()
                }
            } catch (e: Exception) {
                println("Error clearing section highlights: ${e.message}")
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
}
