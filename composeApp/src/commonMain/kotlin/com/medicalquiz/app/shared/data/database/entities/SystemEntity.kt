package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Systems")
data class SystemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val count: Int?
)
