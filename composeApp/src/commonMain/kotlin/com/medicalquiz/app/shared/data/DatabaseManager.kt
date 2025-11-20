package com.medicalquiz.app.shared.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.RoomRawQuery
import com.medicalquiz.app.shared.data.database.AppDatabase
import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.database.getDatabaseBuilder
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

class DatabaseManager(private val dbPath: String) : DatabaseProvider {
    private var database: AppDatabase? = null

    fun init() {
        database = getDatabaseBuilder(dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    private fun getDatabase(): AppDatabase {
        return database ?: throw IllegalStateException("Database not initialized")
    }

    override suspend fun closeDatabase() {
        database?.close()
    }

    override suspend fun getQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> {
        val args = mutableListOf<Any>()
        val whereClauses = mutableListOf<String>()

        subjectIds?.takeIf { it.isNotEmpty() }?.let {
            whereClauses.add(buildMultiValueCondition("q.subId", it, args))
        }

        systemIds?.takeIf { it.isNotEmpty() }?.let {
            whereClauses.add(buildMultiValueCondition("q.sysId", it, args))
        }

        buildPerformanceClause(performanceFilter)?.let { whereClauses.add(it) }

        val sql = buildString {
            append("SELECT q.id FROM Questions q")
            when (performanceFilter) {
                PerformanceFilter.ALL -> {}
                PerformanceFilter.UNANSWERED -> append(" LEFT JOIN logs_summary ls ON ls.qid = q.id")
                else -> append(" JOIN logs_summary ls ON ls.qid = q.id")
            }
            if (whereClauses.isNotEmpty()) {
                append(" WHERE ")
                append(whereClauses.joinToString(" AND "))
            }
            append(" ORDER BY q.id")
        }

        val query = createRoomRawQuery(sql, args)
        return getDatabase().questionDao().getQuestionIds(query)
    }

    override suspend fun getQuestionById(id: Long): Question? {
        val entity = getDatabase().questionDao().getQuestionById(id) ?: return null
        val subName = entity.subId?.let { getDatabase().metadataDao().getSubjectName(it.toLongOrNull() ?: -1) }
        val sysName = entity.sysId?.let { getDatabase().metadataDao().getSystemName(it.toLongOrNull() ?: -1) }
        
        return Question(
            id = entity.id,
            question = entity.question ?: "",
            explanation = entity.explanation ?: "",
            corrAns = entity.corrAns ?: -1,
            title = entity.title,
            mediaName = entity.mediaName,
            otherMedias = entity.otherMedias,
            pplTaken = entity.pplTaken?.toDouble(),
            corrTaken = entity.corrTaken?.toDouble(),
            subId = entity.subId,
            sysId = entity.sysId,
            subName = subName,
            sysName = sysName
        )
    }

    override suspend fun getAnswersForQuestion(questionId: Long): List<Answer> {
        return getDatabase().questionDao().getAnswersForQuestion(questionId).map {
            Answer(
                answerId = it.answerId ?: it.id,
                answerText = it.answerText ?: "",
                correctPercentage = it.correctPercentage,
                qId = it.qId ?: -1L
            )
        }
    }

    override suspend fun getSubjects(): List<Subject> {
        return getDatabase().metadataDao().getSubjects().map {
            Subject(it.id, it.name ?: "", it.count)
        }
    }

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> {
        if (subjectIds.isNullOrEmpty()) {
            return getDatabase().metadataDao().getSystems().map {
                System(it.id, it.name, it.count)
            }
        }

        // Complex query for systems filtered by subjects
        // We need to find distinct sysIds from Questions where subId matches
        val args = mutableListOf<Any>()
        val subConditions = subjectIds.joinToString(" OR ") { id ->
            args.add(id.toString())
            args.add("$id,%")
            args.add("%,$id,%")
            args.add("%,$id")
            "(subId = ? OR subId LIKE ? OR subId LIKE ? OR subId LIKE ?)"
        }
        
        val sql = "SELECT DISTINCT sysId FROM Questions WHERE $subConditions"
        val query = createRoomRawQuery(sql, args)
        val rawSysIds = getDatabase().metadataDao().getDistinctSystemIds(query)
        
        val systemIds = mutableSetOf<Long>()
        rawSysIds.forEach { sysIdStr ->
            if (sysIdStr.isNotBlank()) {
                sysIdStr.split(",").forEach { id ->
                    id.trim().toLongOrNull()?.let { systemIds.add(it) }
                }
            }
        }
        
        if (systemIds.isEmpty()) return emptyList()
        
        return getDatabase().metadataDao().getSystemsByIds(systemIds.toList()).map {
            System(it.id, it.name, it.count)
        }
    }

    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String
    ) {
        // TODO: Implement logging
    }

    override suspend fun flushLogs(): Int {
        return 0
    }

    override suspend fun clearLogs() {
        getDatabase().logSummaryDao().clearLogs()
    }

    override suspend fun clearPendingLogsBuffer() {
        // TODO
    }

    override suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? {
        val summary = getDatabase().logSummaryDao().getSummaryForQuestion(qid) ?: return null
        return QuestionPerformance(
            qid = summary.qid,
            lastCorrect = summary.lastCorrect == 1,
            everCorrect = summary.everCorrect == 1,
            everIncorrect = summary.everIncorrect == 1,
            attempts = 0
        )
    }

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<Any>
    ): String {
        val normalizedIds = ids.distinct()
        return when (normalizedIds.size) {
            0 -> "1=1"
            1 -> {
                args.add(normalizedIds[0].toString())
                "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
            }
            else -> {
                val conditions = normalizedIds.map { id ->
                    args.add(id.toString())
                    "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                }
                "(${conditions.joinToString(" OR ")})"
            }
        }
    }

    private fun buildPerformanceClause(filter: PerformanceFilter): String? = when (filter) {
        PerformanceFilter.ALL -> null
        PerformanceFilter.UNANSWERED -> "ls.qid IS NULL"
        PerformanceFilter.LAST_CORRECT -> "ls.lastCorrect = 1"
        PerformanceFilter.LAST_INCORRECT -> "ls.lastCorrect = 0"
        PerformanceFilter.EVER_CORRECT -> "ls.everCorrect = 1"
        PerformanceFilter.EVER_INCORRECT -> "ls.everIncorrect = 1"
    }

    private fun createRoomRawQuery(sql: String, args: List<Any>): RoomRawQuery {
        return RoomRawQuery(
            sql = sql,
            onBindStatement = { statement ->
                args.forEachIndexed { index, arg ->
                    // SQLite binding is 1-based
                    val bindIndex = index + 1
                    when (arg) {
                        is String -> statement.bindText(bindIndex, arg)
                        is Long -> statement.bindLong(bindIndex, arg)
                        is Int -> statement.bindLong(bindIndex, arg.toLong())
                        is Double -> statement.bindDouble(bindIndex, arg)
                        is Float -> statement.bindDouble(bindIndex, arg.toDouble())
                        is Boolean -> statement.bindLong(bindIndex, if (arg) 1L else 0L)
                        else -> statement.bindText(bindIndex, arg.toString())
                    }
                }
            }
        )
    }
}
