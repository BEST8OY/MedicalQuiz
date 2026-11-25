package com.medqb.app.shared.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.setPlainText(text: AnnotatedString) {
    setClipEntry(ClipEntry(StringSelection(text.text)))
}
