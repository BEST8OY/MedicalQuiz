package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.HighlightSection
import com.medicalquiz.app.shared.data.models.TextHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
    private var currentDbName: String = ""
    private var currentQuestionId: Long = -1

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
        // Clear highlights when database changes
        _questionHighlights.value = emptyList()
        _explanationHighlights.value = emptyList()
        currentQuestionId = -1
    }

    /**
     * Load highlights for a specific question.
     * Call this when navigating to a new question.
     */
    fun loadHighlightsForQuestion(questionId: Long) {
        if (currentDbName.isEmpty()) return
        if (questionId == currentQuestionId) return
        
        currentQuestionId = questionId
        
        scope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val allHighlights = userDataManager.getAllTextHighlightsForQuestion(
                    currentDbName, 
                    questionId
                )
                
                _questionHighlights.value = allHighlights.filter { 
                    it.section == HighlightSection.QUESTION 
                }
                _explanationHighlights.value = allHighlights.filter { 
                    it.section == HighlightSection.EXPLANATION 
                }
            } catch (e: Exception) {
                println("Error loading text highlights: ${e.message}")
                _questionHighlights.value = emptyList()
                _explanationHighlights.value = emptyList()
            } finally {
                _isLoading.value = false
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
        if (currentDbName.isEmpty() || currentQuestionId < 0) return

        scope.launch(Dispatchers.IO) {
            try {
                val highlight = userDataManager.addTextHighlight(
                    dbName = currentDbName,
                    questionId = currentQuestionId,
                    section = section,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    highlightedText = highlightedText,
                    color = color
                )
                
                // Update cache
                when (section) {
                    HighlightSection.QUESTION -> {
                        val updated = (_questionHighlights.value + highlight)
                            .sortedBy { it.startOffset }
                        _questionHighlights.value = updated
                    }
                    HighlightSection.EXPLANATION -> {
                        val updated = (_explanationHighlights.value + highlight)
                            .sortedBy { it.startOffset }
                        _explanationHighlights.value = updated
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
                
                // Update cache
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
                
                // Update cache
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
        if (currentDbName.isEmpty() || currentQuestionId < 0) return

        scope.launch(Dispatchers.IO) {
            try {
                userDataManager.clearTextHighlightsForQuestion(currentDbName, currentQuestionId)
                _questionHighlights.value = emptyList()
                _explanationHighlights.value = emptyList()
            } catch (e: Exception) {
                println("Error clearing highlights: ${e.message}")
            }
        }
    }

    /**
     * Clear highlights for a specific section of the current question.
     */
    fun clearHighlightsForSection(section: HighlightSection) {
        if (currentDbName.isEmpty() || currentQuestionId < 0) return

        scope.launch(Dispatchers.IO) {
            try {
                userDataManager.clearTextHighlightsForQuestion(
                    currentDbName, 
                    currentQuestionId, 
                    section
                )
                
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
}
