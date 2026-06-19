package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import java.awt.datatransfer.StringSelection

internal actual fun Clipboard.setPlainText(text: AnnotatedString) {
    val awtClipboard = nativeClipboard as java.awt.datatransfer.Clipboard
    awtClipboard.setContents(StringSelection(text.text), null)
}
