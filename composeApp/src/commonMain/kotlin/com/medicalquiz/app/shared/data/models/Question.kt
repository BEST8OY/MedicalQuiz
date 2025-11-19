package com.medicalquiz.app.shared.data.models

data class Question(
    val id: Long,
    val question: String,
    val explanation: String,
    val corrAns: Int,
    val title: String?,
    val mediaName: String?,
    val otherMedias: String?,
    val pplTaken: Double?,
    val corrTaken: Double?,
    val subId: String?,
    val sysId: String?,
    val subName: String? = null,
    val sysName: String? = null
)
