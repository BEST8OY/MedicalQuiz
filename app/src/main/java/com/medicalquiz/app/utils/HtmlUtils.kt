package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Environment
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.text.HtmlCompat
import java.io.File

object HtmlUtils {

    private val STYLE_REGEX = Regex("<style[\\s\\S]*?</style>", setOf(RegexOption.IGNORE_CASE))
    private val DATA_ATTR_REGEX = Regex("\\sdata-[a-z0-9-]+=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val CLASS_ATTR_REGEX = Regex("\\sclass=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val STYLE_ATTR_REGEX = Regex("\\sstyle=\"[^\"]*\"", setOf(RegexOption.IGNORE_CASE))
    private val EMPTY_SPAN_REGEX = Regex("<span[^>]*>(.*?)</span>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TABLE_REGEX = Regex("<table[\\s\\S]*?</table>", setOf(RegexOption.IGNORE_CASE))
    private val TABLE_PLACEHOLDER = "[[TABLE_PLACEHOLDER]]"

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
    fun sanitizeForWebView(html: String): String {
        var sanitized = html
        // Keep tables, just remove inline styles and clean attributes
        sanitized = sanitized.replace(STYLE_REGEX, "")
        sanitized = sanitized.replace(DATA_ATTR_REGEX, "")
        // Keep class attributes for CSS styling
        // sanitized = sanitized.replace(CLASS_ATTR_REGEX, "")
        sanitized = sanitized.replace(STYLE_ATTR_REGEX, "")
        
        // Replace image src attributes to use file:// URLs for clickability
        sanitized = sanitized.replace(Regex("""<img([^>]*)\s+src=["']([^"']+)["']""")) { match ->
            val attrs = match.groupValues[1]
            val src = match.groupValues[2]
            val mediaPath = getMediaPath(src)
            if (mediaPath != null) {
                "<img$attrs src=\"file://$mediaPath\""
            } else {
                match.value
            }
        }
        
        return sanitized
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

        val mediaFolder = File(Environment.getExternalStorageDirectory(), "MedicalQuiz/media")
        val mediaFile = File(mediaFolder, fileName)

        return if (mediaFile.exists()) mediaFile.absolutePath else null
    }

    /**
     * Load bitmap from media folder
     */
    fun loadMediaBitmap(fileName: String?): Bitmap? {
        val path = getMediaPath(fileName) ?: return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse comma-separated media files
     */
    fun parseMediaFiles(mediaString: String?): List<String> {
        if (mediaString.isNullOrBlank()) return emptyList()

        return mediaString.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
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
        var htmlText = spanned.toString()
        tables.forEachIndexed { index, table ->
            htmlText = htmlText.replace("$TABLE_PLACEHOLDER$index$TABLE_PLACEHOLDER", table)
        }
        return HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
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
