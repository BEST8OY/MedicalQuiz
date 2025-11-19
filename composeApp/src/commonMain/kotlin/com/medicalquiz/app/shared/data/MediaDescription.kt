package com.medicalquiz.app.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class MediaDescription(
    val imageName: String,
    val title: String,
    val description: String
)
