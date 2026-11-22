package com.medqb.app.shared.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class SubmissionMode {
    INSTANT,
    MANUAL,
}
