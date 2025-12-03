package com.medicalquiz.app.shared.utils

import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler

object HtmlUtils {
    private const val MEDIA_URI_PREFIX = "file:///media/"
    
    // Regex patterns
    private val STYLE_REGEX = Regex("<style[\\s\\S]*?</style>", setOf(RegexOption.IGNORE_CASE))
    private val IMG_TAG_REGEX = Regex("""<img([^>]*)\s+src=['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))
    private val ANCHOR_TAG_REGEX = Regex("""<a([^>]*?)href=([\"'])([^\"']+)\2([^>]*)>""", setOf(RegexOption.IGNORE_CASE))
    private val MEDIA_LINK_REGEX = Regex("""(?i).*\.(jpg|jpeg|png|gif|bmp|webp|mp4|avi|mkv|mov|webm|3gp|mp3|wav|ogg|m4a|aac|flac)(?:$|[?#]).*""")
    private val GENERIC_SRC_REGEX = Regex("""src=\s*['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))
    private val CLASS_ATTR_CAPTURE_REGEX = Regex("""class=(['"])(.*?)\1""", RegexOption.IGNORE_CASE)

    data class QuestionParts(
        val contentHtml: String,
        val hintHtml: String?
    )

    fun extractQuestionHtmlParts(rawHtml: String?): QuestionParts {
        if (rawHtml.isNullOrBlank()) return QuestionParts("", null)
        
        val sanitized = sanitizeForRichText(rawHtml)
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
        GENERIC_SRC_REGEX.findAll(combined).forEach { match ->
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
        val trimmed = html?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return ensureHtmlStructure(trimmed)
    }

    // LRU cache for media path lookups to avoid repeated file-existence checks
    private const val MEDIA_PATH_CACHE_SIZE = 100
    private val mediaPathCache = object : LinkedHashMap<String, String?>(MEDIA_PATH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean = size > MEDIA_PATH_CACHE_SIZE
    }

    fun getMediaPath(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        synchronized(mediaPathCache) {
            if (mediaPathCache.containsKey(fileName)) {
                return mediaPathCache[fileName]
            }
        }
        val path = FileSystemHelper.getMediaFile(fileName)
        synchronized(mediaPathCache) {
            mediaPathCache[fileName] = path
        }
        return path
    }

    fun normalizeFileName(urlOrName: String): String = urlOrName
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
        .trim()

    fun sanitizeForRichText(html: String): String {
        if (html.isBlank()) return ""
        val withoutStyles = removeStyleArtifacts(html)
        val withNormalizedImages = rewriteImageSources(withoutStyles)
        val rewritten = rewriteAnchorTags(withNormalizedImages)
        return ensureHtmlStructure(rewritten.trim())
    }

    private fun removeStyleArtifacts(html: String): String = html
        .replace(STYLE_REGEX, "")

    private fun rewriteImageSources(html: String): String = html.replace(IMG_TAG_REGEX) { match ->
        val attrs = match.groupValues[1]
        val src = match.groupValues[2]
        "<img$attrs src=\"${mediaFileUriFromSource(src)}\""
    }

    private fun rewriteAnchorTags(html: String): String = html.replace(ANCHOR_TAG_REGEX) { match ->
        val before = match.groupValues[1]
        val quote = match.groupValues[2]
        val href = match.groupValues[3]
        val after = match.groupValues[4]
        val newHref = rewriteAnchorHref(href)

        val (finalBefore, finalAfter) = if (newHref.startsWith(MEDIA_URI_PREFIX) || newHref.startsWith("media://")) {
            ensureMetalinkClass(before, after)
        } else {
            before to after
        }

        "<a$finalBefore href=$quote$newHref$quote$finalAfter>"
    }

    private fun ensureMetalinkClass(before: String, after: String): Pair<String, String> {
        var updatedBefore = before
        var updatedAfter = after
        when {
            CLASS_ATTR_CAPTURE_REGEX.containsMatchIn(updatedBefore) -> updatedBefore = appendMetalinkClass(updatedBefore)
            CLASS_ATTR_CAPTURE_REGEX.containsMatchIn(updatedAfter) -> updatedAfter = appendMetalinkClass(updatedAfter)
            else -> updatedBefore += " class=\"metalink\""
        }
        return updatedBefore to updatedAfter
    }

    private fun appendMetalinkClass(target: String): String = CLASS_ATTR_CAPTURE_REGEX.replace(target) { match ->
        val quote = match.groupValues[1]
        val classes = match.groupValues[2]
        val normalized = if (classes.contains("metalink")) classes else "$classes metalink"
        "class=$quote$normalized$quote"
    }

    private fun mediaFileUriFromSource(source: String): String {
        val fileName = normalizeFileName(source)
        return "$MEDIA_URI_PREFIX$fileName"
    }

    private fun rewriteAnchorHref(href: String): String {
        val lower = href.lowercase()
        if (isSpecialProtocol(href)) return href
        if (lower.startsWith("media://")) return mediaFileUriFromSource(href.substringAfter("media://"))
        if (href.contains("/media/") && !lower.startsWith("file://")) {
            return if (href.startsWith("/")) "file://$href" else "file:///$href"
        }
        if (MEDIA_LINK_REGEX.containsMatchIn(href)) {
            return mediaFileUriFromSource(href)
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

    private val HTML_TAG_REGEX = Regex("<\\w+[^>]*?>")

    private fun ensureHtmlStructure(input: String): String {
        if (input.isBlank() || HTML_TAG_REGEX.containsMatchIn(input)) return input

        val normalizedNewlines = input.replace("\r\n", "\n")
        val paragraphs = normalizedNewlines.split(Regex("\n{2,}"))
            .mapNotNull { paragraph ->
                val content = paragraph.trim()
                if (content.isEmpty()) null else {
                    val withLineBreaks = content.replace("\n", "<br />")
                    "<p>$withLineBreaks</p>"
                }
            }

        return if (paragraphs.isEmpty()) "" else paragraphs.joinToString(separator = "")
    }
}
