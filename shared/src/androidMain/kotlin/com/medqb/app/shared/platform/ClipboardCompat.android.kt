package com.medqb.app.shared.platform

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal actual suspend fun Clipboard.setPlainText(text: AnnotatedString) {
    setClipEntry(ClipEntry(ClipData.newPlainText("text", text.text)))
}
