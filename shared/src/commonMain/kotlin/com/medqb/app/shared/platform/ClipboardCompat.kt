package com.medqb.app.shared.platform

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal expect suspend fun Clipboard.setPlainText(text: AnnotatedString)
