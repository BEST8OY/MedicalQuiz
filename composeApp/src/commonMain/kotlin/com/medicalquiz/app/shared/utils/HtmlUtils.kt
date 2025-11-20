package com.medicalquiz.app.shared.utils

import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler

object HtmlUtils {
    private const val MEDIA_FOLDER = "media"
    
    // Regex patterns
    private val STYLE_REGEX = Regex("<style[\\s\\S]*?</style>", setOf(RegexOption.IGNORE_CASE))
    private val DATA_ATTR_REGEX = Regex("\\sdata-[a-z0-9-]+=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val CLASS_ATTR_REGEX = Regex("\\sclass=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    // Updated to handle both single and double quotes for style attributes
    private val STYLE_ATTR_REGEX = Regex("\\sstyle=(?:\"[^\"]*\"|'[^']*')", setOf(RegexOption.IGNORE_CASE))
    private val EMPTY_SPAN_REGEX = Regex("<span[^>]*>(.*?)</span>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TABLE_REGEX = Regex("<table[\\s\\S]*?</table>", setOf(RegexOption.IGNORE_CASE))
    private val IMG_TAG_REGEX = Regex("""<img([^>]*)\s+src=['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))
    private val ANCHOR_TAG_REGEX = Regex("""<a([^>]*?)href=([\"'])([^\"']+)\2([^>]*)>""", setOf(RegexOption.IGNORE_CASE))
    private val MEDIA_LINK_REGEX = Regex("""(?i).*\.(jpg|jpeg|png|gif|bmp|webp|mp4|avi|mkv|mov|webm|3gp|mp3|wav|ogg|m4a|aac|flac)(?:$|[?#]).*""")

    data class QuestionParts(
        val contentHtml: String,
        val hintHtml: String?
    )

    fun extractQuestionHtmlParts(rawHtml: String?): QuestionParts {
        if (rawHtml.isNullOrBlank()) return QuestionParts("", null)
        
        val sanitized = sanitizeForWebView(rawHtml)
        val handler = HintExtractionHandler()
        val parser = KsoupHtmlParser(handler)
        parser.write(sanitized)
        parser.end()
        
        return QuestionParts(
            contentHtml = handler.contentBuilder.toString().trim(),
            hintHtml = handler.hintBuilder.toString().trim().takeIf { it.isNotEmpty() }
        )
    }

    private class HintExtractionHandler : KsoupHtmlHandler {
        val contentBuilder = StringBuilder()
        val hintBuilder = StringBuilder()
        private var inHintDiv = false
        private var hintDivDepth = 0
        private var skipDepth = 0

        private fun appendAttributes(attributes: Map<String, String>, builder: StringBuilder) {
            attributes.forEach { (k, v) ->
                // Escape double quotes to &quot; to ensure valid HTML attribute syntax
                val escaped = v.replace("\"", "&quot;")
                builder.append(" $k=\"$escaped\"")
            }
        }

        override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
            if (skipDepth > 0) {
                skipDepth++
                return
            }

            if (attributes["id"] == "hintdiv") {
                inHintDiv = true
                hintDivDepth = 1
                return
            }

            if (name.equals("button", ignoreCase = true) && attributes["onclick"]?.contains("hintdiv", ignoreCase = true) == true) {
                skipDepth = 1
                return
            }
            
            if (inHintDiv) {
                if (name.equals("div", ignoreCase = true)) hintDivDepth++
                hintBuilder.append("<$name")
                appendAttributes(attributes, hintBuilder)
                hintBuilder.append(">")
            } else {
                // Remove onclick handlers that toggle hint
                val filteredAttrs = attributes.filterNot { (k, v) -> k.equals("onclick", ignoreCase = true) && v.contains("hintdiv") }
                val finalAttrs = filteredAttrs.toMutableMap()
                
                // Fix learning card links
                if (name.equals("a", ignoreCase = true)) {
                    val href = finalAttrs["href"]
                    val learningCardId = finalAttrs["data-learningcard-id"]
                    if ((href.isNullOrBlank() || href.contains("{{")) && !learningCardId.isNullOrBlank()) {
                        val anchor = finalAttrs["data-anker"]
                        val newHref = if (anchor != null) "learningcard://$learningCardId/$anchor" else "learningcard://$learningCardId"
                        finalAttrs["href"] = newHref
                    }
                }

                contentBuilder.append("<$name")
                appendAttributes(finalAttrs, contentBuilder)
                contentBuilder.append(">")
            }
        }

        override fun onText(text: String) {
            if (skipDepth > 0) return
            if (inHintDiv) {
                hintBuilder.append(text)
            } else {
                contentBuilder.append(text)
            }
        }

        override fun onCloseTag(name: String, isImplied: Boolean) {
            if (skipDepth > 0) {
                skipDepth--
                return
            }

            if (inHintDiv) {
                if (name.equals("div", ignoreCase = true)) {
                    hintDivDepth--
                    if (hintDivDepth == 0) {
                        inHintDiv = false
                        return
                    }
                }
                hintBuilder.append("</$name>")
            } else {
                contentBuilder.append("</$name>")
            }
        }

        override fun onEnd() {
            // No-op
        }
    }

    fun collectMediaFiles(question: com.medicalquiz.app.shared.data.models.Question): List<String> {
        val media = mutableSetOf<String>()

        // DB field references
        question.mediaName?.takeIf { it.isNotBlank() }?.let { media.add(normalizeFileName(it)) }
        parseMediaFiles(question.otherMedias).forEach { media.add(normalizeFileName(it)) }

        // HTML references in question body/explanation
        val combined = question.question + " " + question.explanation

        // Helper: add if valid
        fun addCandidate(candidate: String?) {
            if (candidate.isNullOrBlank()) return
            val normalized = normalizeFileName(candidate)
            if (normalized.isNotBlank()) media.add(normalized)
        }

        // Extract img src attributes
        IMG_TAG_REGEX.findAll(combined).forEach { match ->
            addCandidate(match.groupValues[2])
        }

        // Extract generic src attributes (video/source/audio)
        val srcRegex = Regex("""src=\s*['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))
        srcRegex.findAll(combined).forEach { match ->
            val src = match.groupValues[1]
            if (!isSpecialProtocol(src)) addCandidate(src)
        }

        // Extract anchors that point to media links
        ANCHOR_TAG_REGEX.findAll(combined).forEach { match ->
            val href = match.groupValues[3]
            if (MEDIA_LINK_REGEX.containsMatchIn(href) && !isSpecialProtocol(href)) addCandidate(href)
        }

        return media.toList()
    }

    fun normalizeAnswerHtml(html: String?): String {
        return html?.trim() ?: ""
    }

    fun getMediaPath(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        return FileSystemHelper.getMediaFile(fileName)
    }

    fun normalizeFileName(urlOrName: String): String = urlOrName
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
        .trim()

    fun sanitizeForWebView(html: String): String = html
        .replace(STYLE_REGEX, "")
        //.replace(DATA_ATTR_REGEX, "") // Keep data attributes for tooltips and links
        .replace(STYLE_ATTR_REGEX, "")
        .replace(IMG_TAG_REGEX) { match ->
            val attrs = match.groupValues[1]
            val src = match.groupValues[2]
            val fileName = normalizeFileName(src)
            "<img$attrs src=\"file:///media/$fileName\""
        }
        .replace(ANCHOR_TAG_REGEX) { match ->
            val before = match.groupValues[1]
            val quote = match.groupValues[2]
            val href = match.groupValues[3]
            val after = match.groupValues[4]
            val newHref = rewriteAnchorHref(href)
            
            var newBefore = before
            var newAfter = after
            
            if (newHref.startsWith("file:///media/") || newHref.startsWith("media://")) {
                 val classRegex = Regex("class=([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                 if (classRegex.containsMatchIn(newBefore)) {
                     newBefore = newBefore.replace(classRegex) { m ->
                         val q = m.groupValues[1]
                         val c = m.groupValues[2]
                         "class=$q$c metalink$q"
                     }
                 } else if (classRegex.containsMatchIn(newAfter)) {
                     newAfter = newAfter.replace(classRegex) { m ->
                         val q = m.groupValues[1]
                         val c = m.groupValues[2]
                         "class=$q$c metalink$q"
                     }
                 } else {
                     newBefore += " class=\"metalink\""
                 }
            }

            "<a$newBefore href=$quote$newHref$quote$newAfter>"
        }

    private fun rewriteAnchorHref(href: String): String {
        val lower = href.lowercase()
        if (isSpecialProtocol(href)) return href
        if (lower.startsWith("media://")) return "file:///media/" + normalizeFileName(href.substringAfter("media://"))
        if (href.contains("/media/") && !lower.startsWith("file://")) {
            return if (href.startsWith("/")) "file://$href" else "file:///$href"
        }
        if (MEDIA_LINK_REGEX.containsMatchIn(href)) {
            return "file:///media/" + normalizeFileName(href)
        }
        return href
    }

    private fun isSpecialProtocol(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:") || lower.startsWith("data:") || lower.startsWith("javascript:")
    }
    
    fun parseMediaFiles(mediaString: String?): List<String> {
        if (mediaString.isNullOrBlank()) return emptyList()
        return mediaString.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
