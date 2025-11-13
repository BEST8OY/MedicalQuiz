package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.text.Html
import android.text.Spanned
import android.widget.TextView
import java.io.File

object HtmlUtils {
    
    /**
     * Convert HTML string to Spanned text for TextView
     */
    fun fromHtml(html: String?): Spanned {
        if (html.isNullOrBlank()) return Html.fromHtml("", Html.FROM_HTML_MODE_COMPACT)
        
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }
    
    /**
     * Set HTML content to TextView
     */
    fun setHtmlText(textView: TextView, html: String?) {
        textView.text = fromHtml(html)
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
     * Strip HTML tags from text
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
    }
}
