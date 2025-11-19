package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Subjects")
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val count: Int?
)
