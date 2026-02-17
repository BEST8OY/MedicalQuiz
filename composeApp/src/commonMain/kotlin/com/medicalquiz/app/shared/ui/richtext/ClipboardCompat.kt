package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

internal expect suspend fun Clipboard.setPlainText(text: AnnotatedString)
