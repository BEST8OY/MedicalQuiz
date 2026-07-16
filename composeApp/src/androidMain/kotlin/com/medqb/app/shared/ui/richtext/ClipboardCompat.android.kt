package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal actual fun Clipboard.setPlainText(text: AnnotatedString) {
    setText(text)
}
