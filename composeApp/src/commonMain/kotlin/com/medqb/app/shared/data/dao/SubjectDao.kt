package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SubjectDao(
    private val getConnection: () -> SQLiteConnection,
    private val mutex: Mutex,
) {
    suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "SELECT id, name, count FROM Subjects ORDER BY name"
            val subjects = mutableListOf<Subject>()
            getConnection().prepare(sql).use { stmt ->
                while (stmt.step()) {
                    subjects.add(Subject(
                        id = stmt.getLong(0),
                        name = if (stmt.isNull(1)) "" else stmt.getText(1),
                        count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                    ))
                }
            }
            subjects
        }
    }

    suspend fun getSystems(subjectIds: List<Long>?): List<System> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val systems = mutableListOf<System>()

            if (subjectIds.isNullOrEmpty()) {
                val sql = "SELECT id, name, count FROM Systems ORDER BY name"
                getConnection().prepare(sql).use { stmt ->
                    while (stmt.step()) {
                        systems.add(System(
                            id = stmt.getLong(0),
                            name = if (stmt.isNull(1)) "" else stmt.getText(1),
                            count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                        ))
                    }
                }
            } else {
                val placeholders = subjectIds.joinToString(",") { "?" }
                val sysIdSql = "SELECT DISTINCT sysId FROM SubjectsSystems WHERE subId IN ($placeholders)"
                val sysIds = mutableListOf<Long>()

                getConnection().prepare(sysIdSql).use { stmt ->
                    subjectIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                    while (stmt.step()) {
                        sysIds.add(stmt.getLong(0))
                    }
                }

                if (sysIds.isNotEmpty()) {
                    val sysPlaceholders = sysIds.joinToString(",") { "?" }
                    val sql = "SELECT id, name, count FROM Systems WHERE id IN ($sysPlaceholders) ORDER BY name"
                    getConnection().prepare(sql).use { stmt ->
                        sysIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                        while (stmt.step()) {
                            systems.add(System(
                                id = stmt.getLong(0),
                                name = if (stmt.isNull(1)) "" else stmt.getText(1),
                                count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                            ))
                        }
                    }
                }
            }
            systems
        }
    }

    fun getSubjectNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""

        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Subjects WHERE id IN ($placeholders)"

        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }

    fun getSystemNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""

        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Systems WHERE id IN ($placeholders)"

        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }
}
