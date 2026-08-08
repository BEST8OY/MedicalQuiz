package com.medqb.app.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "text_highlights",
    indices = [Index(value = ["db_name", "question_id", "section"])]
)
data class TextHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "db_name")
    val dbName: String,
    @ColumnInfo(name = "question_id")
    val questionId: Long,
    @ColumnInfo(name = "section")
    val section: String,
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,
    @ColumnInfo(name = "highlighted_text")
    val highlightedText: String,
    @ColumnInfo(name = "color")
    val color: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
