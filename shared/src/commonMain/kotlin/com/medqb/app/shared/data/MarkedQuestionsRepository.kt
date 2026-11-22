package com.medqb.app.shared.data

import kotlinx.coroutines.flow.Flow

interface MarkedQuestionsRepository {
    suspend fun isMarked(dbName: String, questionId: Long): Boolean
    suspend fun getMarkedQuestionIds(dbName: String): Set<Long>
    fun observeMarkedQuestionIds(dbName: String): Flow<Set<Long>>
    suspend fun setMarked(dbName: String, questionId: Long, marked: Boolean): Boolean
    suspend fun toggleMark(dbName: String, questionId: Long): Boolean
    suspend fun clearForDatabase(dbName: String)
}
