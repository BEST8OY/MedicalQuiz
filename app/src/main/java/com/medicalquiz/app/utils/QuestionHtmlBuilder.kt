package com.medicalquiz.app.utils

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question

/**
 * Build HTML content for a Question and its answers.
 */
object QuestionHtmlBuilder {

    fun build(question: Question, answers: List<Answer>): String {
        val questionBody = HtmlUtils.sanitizeForWebView(question.question)
        val explanationSource = question.explanation.ifBlank { "Explanation not provided." }
        val explanation = HtmlUtils.sanitizeForWebView(explanationSource)

        val answersHtml = if (answers.isEmpty()) {
            """
                <p class="empty-state">No answers available for this question.</p>
            """.trimIndent()
        } else {
            answers.mapIndexed { index, answer ->
                val label = ('A'.code + index).toChar()
                val sanitizedAnswer = HtmlUtils.normalizeAnswerHtml(
                    HtmlUtils.sanitizeForWebView(answer.answerText)
                )
                """
                <button type="button"
                        class="answer-button"
                        id="answer-${answer.answerId}"
                        value="${answer.answerId}">
                    <span class="answer-label">$label.</span>
                    <span class="answer-text">$sanitizedAnswer</span>
                </button>
                """.trimIndent()
            }.joinToString("\n")
        }

        val explanationBlock = """
            <section id="explanation" class="explanation hidden">
                <h3>Explanation</h3>
                $explanation
            </section>
        """.trimIndent()

        val scriptBlock = """
            <script>
                (function() {
                    function onReady(callback) {
                        if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', callback);
                        } else {
                            callback();
                        }
                    }

                    function bindAnswerButtons() {
                        var buttons = document.querySelectorAll('.answer-button');
                        buttons.forEach(function(button) {
                            button.addEventListener('click', function() {
                                if (button.classList.contains('locked')) { return; }
                                var answerId = button.value || button.getAttribute('value');
                                if (window.AndroidBridge && answerId) {
                                    window.AndroidBridge.onAnswerSelected(String(answerId));
                                }
                            });
                        });
                    }

                    function setHintVisibility(visible, meta) {
                        var body = document.body;
                        if (!body) { return; }
                        body.classList.toggle('hint-visible', !!visible);
                        if (visible && meta && meta.auto) {
                            body.classList.add('hint-auto');
                        }
                        if (!visible || (meta && meta.manual)) {
                            body.classList.remove('hint-auto');
                        }
                    }

                    function toggleHintVisibility() {
                        var body = document.body;
                        if (!body) { return; }
                        var nextState = !body.classList.contains('hint-visible');
                        setHintVisibility(nextState, { manual: true });
                    }

                    function initializeHintBehavior() {
                        var body = document.body;
                        if (!body) { return; }
                        body.classList.remove('answer-revealed');
                        body.classList.remove('hint-visible', 'hint-auto');
                        var hint = document.getElementById('hintdiv');
                        if (!hint) { return; }
                        setHintVisibility(false, { manual: true });
                        var buttons = document.querySelectorAll('button[onclick]');
                        buttons.forEach(function(button) {
                            var handler = (button.getAttribute('onclick') || '').toLowerCase();
                            if (handler.indexOf('hintdiv') === -1) { return; }
                            button.onclick = function(event) {
                                event.preventDefault();
                                toggleHintVisibility();
                            };
                        });
                    }

                    onReady(function() {
                        // Notify the host that the DOM is ready for this question
                        try {
                            var qid = document.querySelector('.quiz-block') && document.querySelector('.quiz-block').getAttribute('data-question-id');
                            if (qid) {
                                console.log('Quiz: DOM ready for question ' + qid);
                                if (window.AndroidBridge && window.AndroidBridge.domReady) {
                                    window.AndroidBridge.domReady(String(qid));
                                }
                            }
                        } catch (e) { console.error('Error reporting domReady', e); }
                        console.log('Quiz: DOM ready - binding answer handlers');
                        try { bindAnswerButtons(); } catch (e) { console.error('Quiz: bindAnswerButtons failed', e); }
                        initializeHintBehavior();
                    });

                    window.applyAnswerState = function(correctId, selectedId) {
                        console.log('Quiz: applyAnswerState', correctId, selectedId);
                        var buttons = document.querySelectorAll('.answer-button');
                        buttons.forEach(function(button) {
                            var id = parseInt(button.value || button.getAttribute('value'));
                            if (isNaN(id)) { return; }
                            button.disabled = true;
                            button.classList.add('locked');
                            button.classList.remove('correct', 'incorrect');
                            if (id === correctId) { button.classList.add('correct'); }
                            if (id === selectedId && selectedId !== correctId) { button.classList.add('incorrect'); }
                        });
                    };

                    window.onerror = function(message, source, lineno, colno, error) {
                        console.error('Quiz JS error:', message, source, lineno, colno, error && error.stack);
                    };

                    window.setAnswerFeedback = function(text) {
                        var feedback = document.getElementById('answer-feedback');
                        if (feedback) { feedback.textContent = text; feedback.classList.remove('hidden'); }
                    };

                    window.revealExplanation = function() {
                        var section = document.getElementById('explanation');
                        if (section) { section.classList.remove('hidden'); }
                    };

                    window.markAnswerRevealed = function() {
                        var body = document.body; if (!body) { return; }
                        body.classList.add('answer-revealed'); setHintVisibility(true, { auto: true });
                    };
                })();
            </script>
        """.trimIndent()

        return """
            <article class="quiz-block" data-question-id="${question.id}">
                <section class="question-body">$questionBody</section>
                <div id="answer-feedback" class="answer-feedback hidden"></div>
                <section class="answers">$answersHtml</section>
                $explanationBlock
            </article>
            $scriptBlock
        """.trimIndent()
    }
}
