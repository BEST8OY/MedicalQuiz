package com.medicalquiz.app.shared.ui.richtext

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal actual fun Clipboard.setPlainText(text: AnnotatedString) {
    nativeClipboard.setPrimaryClip(
        ClipData.newPlainText("text", text.text)
    )
}
