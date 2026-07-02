package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal actual fun Clipboard.setPlainText(text: AnnotatedString) {
    val awtClipboard = Toolkit.getDefaultToolkit().systemClipboard
    awtClipboard.setContents(StringSelection(text.text), null)
}
