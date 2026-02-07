package com.medicalquiz.app.shared.utils

import com.medicalquiz.app.shared.ui.media.MediaType

/**
 * Centralized utility for media type detection.
 * Single source of truth for determining media types from file extensions.
 */
object MediaTypeUtils {

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tiff", "tif", "svg"
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "avi", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "aiff", "au"
    )

    private val HTML_EXTENSIONS = setOf("html", "htm")

    /**
     * Determines MediaType from a file extension.
     * @param extension The file extension (with or without leading dot)
     * @return The corresponding MediaType, or UNKNOWN if not recognized
     */
    fun fromExtension(extension: String): MediaType {
        val ext = extension.lowercase().removePrefix(".")
        return when {
            ext in IMAGE_EXTENSIONS -> MediaType.IMAGE
            ext in VIDEO_EXTENSIONS -> MediaType.VIDEO
            ext in AUDIO_EXTENSIONS -> MediaType.AUDIO
            ext in HTML_EXTENSIONS -> MediaType.HTML
            else -> MediaType.UNKNOWN
        }
    }

    /**
     * Determines MediaType from a filename.
     * @param fileName The filename to check
     * @return The corresponding MediaType, or UNKNOWN if not recognized
     */
    fun fromFileName(fileName: String): MediaType {
        return fromExtension(fileName.substringAfterLast('.', ""))
    }

    /**
     * Checks if the given file extension represents an image.
     * @param extension The file extension to check
     * @return true if it's an image type
     */
    fun isImage(extension: String): Boolean {
        return extension.lowercase().removePrefix(".") in IMAGE_EXTENSIONS
    }

    /**
     * Checks if the given file extension represents a video.
     * @param extension The file extension to check
     * @return true if it's a video type
     */
    fun isVideo(extension: String): Boolean {
        return extension.lowercase().removePrefix(".") in VIDEO_EXTENSIONS
    }

    /**
     * Checks if the given file extension represents audio.
     * @param extension The file extension to check
     * @return true if it's an audio type
     */
    fun isAudio(extension: String): Boolean {
        return extension.lowercase().removePrefix(".") in AUDIO_EXTENSIONS
    }

    /**
     * Checks if the given file extension represents HTML.
     * @param extension The file extension to check
     * @return true if it's an HTML type
     */
    fun isHtml(extension: String): Boolean {
        return extension.lowercase().removePrefix(".") in HTML_EXTENSIONS
    }

    /**
     * Checks if the given file is a media file (image, video, or audio).
     * @param fileName The filename to check
     * @return true if it's a recognized media type
     */
    fun isMediaFile(fileName: String): Boolean {
        return fromFileName(fileName) != MediaType.UNKNOWN
    }

    /**
     * Gets all supported image extensions.
     * @return Set of supported image extensions
     */
    fun getImageExtensions(): Set<String> = IMAGE_EXTENSIONS

    /**
     * Gets all supported video extensions.
     * @return Set of supported video extensions
     */
    fun getVideoExtensions(): Set<String> = VIDEO_EXTENSIONS

    /**
     * Gets all supported audio extensions.
     * @return Set of supported audio extensions
     */
    fun getAudioExtensions(): Set<String> = AUDIO_EXTENSIONS

    /**
     * Gets all supported HTML extensions.
     * @return Set of supported HTML extensions
     */
    fun getHtmlExtensions(): Set<String> = HTML_EXTENSIONS
}
