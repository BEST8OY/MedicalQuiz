package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity

@Entity(tableName = "SubjectsSystems", primaryKeys = ["subId", "sysId"])
data class SubjectsSystemsEntity(
    val subId: Long,
    val sysId: Long,
    val count: Int?
)
