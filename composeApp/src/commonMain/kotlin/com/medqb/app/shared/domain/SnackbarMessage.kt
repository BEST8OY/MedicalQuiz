package com.medqb.app.shared.domain

import androidx.compose.material3.SnackbarDuration

sealed interface SnackbarMessage {
    val message: String

    data class Simple(override val message: String) : SnackbarMessage

    data class Action(
        override val message: String,
        val actionLabel: String,
        val duration: SnackbarDuration = SnackbarDuration.Long,
        val onActionPerformed: () -> Unit,
    ) : SnackbarMessage
}
