package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Questions")
data class QuestionEntity(
    @PrimaryKey val id: Long,
    val question: String,
    val explanation: String,
    val corrAns: Int,
    val title: String?,
    val mediaName: String?,
    val otherMedias: String?,
    val pplTaken: Double?,
    val corrTaken: Double?,
    val subId: String?,
    val sysId: String?
)
