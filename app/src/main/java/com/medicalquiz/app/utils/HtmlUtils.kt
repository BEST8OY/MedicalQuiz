package com.medicalquiz.app.utils

import com.medicalquiz.app.data.models.Question

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Environment
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class QuestionHtmlParts(
    val contentHtml: String,
    val hintHtml: String?
)

object HtmlUtils {

    private const val TAG = "HtmlUtils"
    private const val TABLE_PLACEHOLDER = "[[TABLE_PLACEHOLDER]]"
    private const val MEDIA_FOLDER = "MedicalQuiz/media"

    private val STYLE_REGEX = Regex("<style[\\s\\S]*?</style>", setOf(RegexOption.IGNORE_CASE))
    private val DATA_ATTR_REGEX = Regex("\\sdata-[a-z0-9-]+=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val CLASS_ATTR_REGEX = Regex("\\sclass=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val STYLE_ATTR_REGEX = Regex("\\sstyle=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val EMPTY_SPAN_REGEX = Regex("<span[^>]*>(.*?)</span>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TABLE_REGEX = Regex("<table[\\s\\S]*?</table>", setOf(RegexOption.IGNORE_CASE))
    // Find <img ... src="..."> with attributes captured in group 1 and the src in group 2
    private val IMG_TAG_REGEX = Regex("""<img([^>]*)\s+src=['\"]([^'\"]+)['\"]""", setOf(RegexOption.IGNORE_CASE))

    // Anchor tag capturing: pre-href attributes, quote char, href value, post-href attributes
    private val ANCHOR_TAG_REGEX = Regex("""<a([^>]*?)href=([\"'])([^\"']+)\2([^>]*)>""", setOf(RegexOption.IGNORE_CASE))

    // Match common media file extensions on the end of a path or query-free url
    private val MEDIA_LINK_REGEX = Regex("""(?i).*\.(jpg|jpeg|png|gif|bmp|webp|mp4|avi|mkv|mov|webm|3gp|mp3|wav|ogg|m4a|aac|flac|html|htm)(?:$|[?#]).*""")
    private val SINGLE_PARAGRAPH_REGEX = Regex(
        pattern = "^\\s*<p>([\\s\\S]*)</p>\\s*$",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val mediaPathCache = ConcurrentHashMap<String, String>()
    private val missingMediaCache = ConcurrentHashMap.newKeySet<String>()
    private val dataUriCache = ConcurrentHashMap<String, String>()

    private data class SpanTransform(val className: String, val replacementTag: String)

    private val spanTransforms = listOf(
        SpanTransform("wichtig", "strong"),
        SpanTransform("selected", "mark"),
        SpanTransform("scientific-name", "em"),
        SpanTransform("nowrap", "span")
    )

    /**
     * Convert HTML string to Spanned text for TextView
     */
    fun fromHtml(context: Context, html: String?): Spanned {
        if (html.isNullOrBlank()) return HtmlCompat.fromHtml("", HtmlCompat.FROM_HTML_MODE_LEGACY)
        val (sanitized, tables) = sanitizeHtml(html)
        val imageGetter = MediaImageGetter(context)
        val spanned = HtmlCompat.fromHtml(sanitized, HtmlCompat.FROM_HTML_MODE_LEGACY, imageGetter, null)
        return restoreTables(spanned, tables)
    }

    /**
     * Set HTML content to TextView with link + image support
     */
    fun setHtmlText(textView: TextView, html: String?, enableLinks: Boolean = true) {
        textView.text = fromHtml(textView.context, html)
        if (enableLinks) {
            textView.movementMethod = LinkMovementMethod.getInstance()
        } else {
            textView.movementMethod = null
        }
    }

    private fun sanitizeHtml(html: String): Pair<String, List<String>> {
        var sanitized = html
        val tables = mutableListOf<String>()
        sanitized = TABLE_REGEX.replace(sanitized) { match ->
            tables.add(match.value)
            "$TABLE_PLACEHOLDER${tables.size - 1}$TABLE_PLACEHOLDER"
        }
        sanitized = sanitized.replace(STYLE_REGEX, "")
        spanTransforms.forEach { transform ->
            sanitized = replaceSpan(sanitized, transform)
        }
        sanitized = sanitized.replace(DATA_ATTR_REGEX, "")
        sanitized = sanitized.replace(STYLE_ATTR_REGEX, "")
        sanitized = sanitized.replace(CLASS_ATTR_REGEX, "")
        sanitized = sanitized.replace(EMPTY_SPAN_REGEX) { match -> match.groupValues[1] }
        return sanitized to tables
    }
    
    /**
     * Public sanitize method for WebViewRenderer
     */
    fun sanitizeForWebView(html: String): String = html
        .replace(STYLE_REGEX, "")
        .replace(DATA_ATTR_REGEX, "")
        // Keep class attributes for CSS styling
        .replace(STYLE_ATTR_REGEX, "")
        .replace(IMG_TAG_REGEX) { match ->
            val attrs = match.groupValues[1]
            val src = match.groupValues[2]
            val fileName = normalizeFileName(src)
            // Convert image references to file:///media/<filename> for clickable media handling
            "<img$attrs src=\"file:///media/$fileName\""
        }
        // Rewrite anchor links that point to media so they become file:///media/<filename>
        .replace(ANCHOR_TAG_REGEX) { match ->
            val before = match.groupValues[1]
            val quote = match.groupValues[2]
            val href = match.groupValues[3]
            val after = match.groupValues[4]

            val newHref = rewriteAnchorHref(href)
            "<a$before href=$quote$newHref$quote$after>"
        }

    fun extractQuestionHtmlParts(rawHtml: String): QuestionHtmlParts {
        val sanitized = sanitizeForWebView(rawHtml)
        val document = Jsoup.parseBodyFragment(sanitized)
        val hintElement = document.getElementById("hintdiv")
        val hintHtml = hintElement?.html()?.trim()?.takeIf { it.isNotBlank() }
        hintElement?.remove()
        val remaining = document.body().html().trim()
        return QuestionHtmlParts(
            contentHtml = remaining,
            hintHtml = hintHtml
        )
    }

    /**
     * Collect media files from question fields and HTML content.
     * This returns distinct file names (image, audio, video, html) that appear
     * in the question body, explanation, mediaName and otherMedias fields.
     */
    fun collectMediaFiles(question: Question): List<String> {
        val media = linkedSetOf<String>()

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

    // Normalize file name part from URL or relative path
    private fun normalizeFileName(urlOrName: String): String = urlOrName
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
        .trim()

    private fun isSpecialProtocol(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:") || lower.startsWith("data:") || lower.startsWith("javascript:")
    }

    // Convert various href forms into the app's file:///media/<filename> or leave as-is
    private fun rewriteAnchorHref(href: String): String {
        val lower = href.lowercase()
        if (isSpecialProtocol(href)) return href
        // media:// scheme (legacy) => file:///media/<filename>
        if (lower.startsWith("media://")) return "file:///media/" + normalizeFileName(href.substringAfter("media://"))

        // Already an absolute file path with /media/ segment
        if (href.contains("/media/") && !lower.startsWith("file://")) {
            return if (href.startsWith("/")) "file://$href" else "file:///$href"
        }

        // End with known media extension: make it a media file reference
        if (MEDIA_LINK_REGEX.containsMatchIn(href)) {
            return "file:///media/" + normalizeFileName(href)
        }

        // Otherwise leave alone
        return href
    }

    fun normalizeAnswerHtml(html: String): String {
        if (html.isBlank()) return ""
        val trimmed = html.trim()
        val match = SINGLE_PARAGRAPH_REGEX.matchEntire(trimmed)
        if (match != null) {
            val inner = match.groupValues[1]
            val containsNestedParagraph = inner.contains("<p", true) || inner.contains("</p>", true)
            if (!containsNestedParagraph) {
                return inner.trim()
            }
        }
        return trimmed
    }

    private fun replaceSpan(html: String, transform: SpanTransform): String {
        val classPattern = Regex.escape(transform.className)
        val regex = Regex(
            "<span[^>]*class=\"[^\"]*$classPattern[^\"]*\"[^>]*>(.*?)</span>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return regex.replace(html) { match ->
            val inner = match.groupValues[1]
            "<${transform.replacementTag}>$inner</${transform.replacementTag}>"
        }
    }

    /**
     * Get media file path from MedicalQuiz/media folder
     */
    fun getMediaPath(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        if (missingMediaCache.contains(fileName)) return null
        mediaPathCache[fileName]?.let { return it }

        val storageRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (storageRoot == null) {
            Log.w(TAG, "External storage directory unavailable; cannot resolve media for $fileName")
            missingMediaCache.add(fileName)
            return null
        }

        val resolvedPath = runCatching {
            val mediaFolder = File(storageRoot, MEDIA_FOLDER)
            val mediaFile = File(mediaFolder, fileName)
            if (mediaFile.exists() && mediaFile.canRead()) mediaFile.absolutePath else null
        }.getOrElse {
            Log.w(TAG, "Failed to resolve media path for $fileName", it)
            null
        }

        return resolvedPath?.also { mediaPathCache[fileName] = it } ?: run {
            missingMediaCache.add(fileName)
            null
        }
    }

    /**
     * Create a base64 data URI for an image file
     */
    private fun createImageDataUri(fileName: String): String? {
        // Check cache first
        dataUriCache[fileName]?.let { return it }

        val path = getMediaPath(fileName) ?: return null
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null

            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mimeType = getImageMimeType(fileName)
            val dataUri = "data:$mimeType;base64,$base64"

            // Cache the result
            dataUriCache[fileName] = dataUri
            dataUri
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create data URI for $fileName", e)
            null
        }
    }

    /**
     * Get MIME type for image files
     */
    private fun getImageMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "image/jpeg" // fallback
        }
    }

    /**
     * Parse comma-separated media files with better error handling
     */
    fun parseMediaFiles(mediaString: String?): List<String> {
        if (mediaString.isNullOrBlank()) return emptyList()

        return mediaString.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 255 } // Reasonable filename length limit
            .filter { it.matches(Regex("^[\\w\\-\\.\\s]+$")) } // Basic filename validation
            .distinct()
    }

    /**
     * Strip HTML tags from text (for plain-text fallbacks)
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val (sanitized, tables) = sanitizeHtml(html)
        var text = HtmlCompat.fromHtml(sanitized, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        tables.forEachIndexed { index, tableHtml ->
            text = text.replace("$TABLE_PLACEHOLDER$index$TABLE_PLACEHOLDER", tableHtml)
        }
        return text
    }

    private fun restoreTables(spanned: Spanned, tables: List<String>): Spanned {
        val htmlText = tables.foldIndexed(spanned.toString()) { index, acc, table ->
            acc.replace("$TABLE_PLACEHOLDER$index$TABLE_PLACEHOLDER", table)
        }
        return HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    /**
     * Clear media path caches (useful when media files change)
     * Includes size limits to prevent unbounded memory growth
     */
    fun clearMediaCaches() {
        mediaPathCache.clear()
        missingMediaCache.clear()
        dataUriCache.clear()
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): Map<String, Int> = mapOf(
        "mediaPathCache" to mediaPathCache.size,
        "missingMediaCache" to missingMediaCache.size,
        "dataUriCache" to dataUriCache.size
    )

    /**
     * Trim caches if they exceed reasonable limits (call periodically)
     */
    fun trimCaches(maxSize: Int = 1000) {
        if (mediaPathCache.size > maxSize) {
            // Keep most recently used items (simple LRU approximation)
            val entries = mediaPathCache.entries.toList()
            mediaPathCache.clear()
            entries.takeLast(maxSize).forEach { mediaPathCache[it.key] = it.value }
        }

        if (dataUriCache.size > maxSize) {
            val entries = dataUriCache.entries.toList()
            dataUriCache.clear()
            entries.takeLast(maxSize).forEach { dataUriCache[it.key] = it.value }
        }

        if (missingMediaCache.size > maxSize / 10) { // Smaller limit for missing cache
            missingMediaCache.clear()
        }
    }

    private class MediaImageGetter(private val context: Context) : Html.ImageGetter {
        override fun getDrawable(source: String?): Drawable? {
            if (source.isNullOrBlank()) return null
            val path = getMediaPath(source) ?: return null
            val drawable = Drawable.createFromPath(path) ?: return null
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            return drawable
        }
    }
}
