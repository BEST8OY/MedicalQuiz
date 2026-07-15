package com.medqb.app.shared.ui.richtext

// This file previously contained the custom text selection gesture system
// (selectableHighlightGestures modifier, TextSelectionState, word-boundary
// snapping, handle drag logic). All of that has been replaced by native
// Compose selection via BasicTextField(state = TextFieldState, readOnly = true)
// in SelectableHighlightText.kt.
//
// Remaining utilities are kept for potential future use or are referenced
// elsewhere in the richtext subsystem.
