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
    private val STYLE_ATTR_REGEX = Regex("\\sstyle=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val EMPTY_SPAN_REGEX = Regex("<span[^>]*>(.*?)</span>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TABLE_REGEX = Regex("<table[\\s\\S]*?</table>", setOf(RegexOption.IGNORE_CASE))
    private val IMG_TAG_REGEX = Regex("""<img([^>]*)\s+src=['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))
    private val ANCHOR_TAG_REGEX = Regex("""<a([^>]*?)href=([\"'])([^\"']+)\2([^>]*)>""", setOf(RegexOption.IGNORE_CASE))
    private val MEDIA_LINK_REGEX = Regex("""(?i).*\.(jpg|jpeg|png|gif|bmp|webp|mp4|avi|mkv|mov|webm|3gp|mp3|wav|ogg|m4a|aac|flac|html|htm)(?:$|[?#]).*""")

    data class QuestionParts(
        val contentHtml: String,
        val hintHtml: String?
    )

    fun extractQuestionHtmlParts(rawHtml: String?): QuestionParts {
        if (rawHtml.isNullOrBlank()) return QuestionParts("", null)
        
        // Simple implementation: check for a hint separator if any, otherwise return full content
        // For now, we'll just return the sanitized content as the question
        return QuestionParts(sanitizeForWebView(rawHtml), null)
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
        .replace(DATA_ATTR_REGEX, "")
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
            "<a$before href=$quote$newHref$quote$after>"
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
