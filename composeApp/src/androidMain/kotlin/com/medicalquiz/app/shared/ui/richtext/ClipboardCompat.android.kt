package com.medicalquiz.app.shared.ui.richtext

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal actual suspend fun Clipboard.setPlainText(text: AnnotatedString) {
    (nativeClipboard as ClipboardManager).setPrimaryClip(
        ClipData.newPlainText("text", text.text)
    )
}
