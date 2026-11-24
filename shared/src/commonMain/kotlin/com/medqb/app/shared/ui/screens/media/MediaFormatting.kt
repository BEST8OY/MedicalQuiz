package com.medqb.app.shared.ui.screens.media

/**
 * Formats a media duration in [millis] into `M:SS` or `H:MM:SS`.
 */
internal fun formatMediaDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val secStr = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        val minStr = minutes.toString().padStart(2, '0')
        "$hours:$minStr:$secStr"
    } else {
        "$minutes:$secStr"
    }
}
