package com.medqb.app.shared.data.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusFilterTest {

    @Test
    fun defaultFilterHasNoStatusesSelectedAndAllAttemptsScope() {
        val filter = StatusFilter.DEFAULT
        assertEquals(emptySet(), filter.statuses)
        assertEquals(StatusScope.ALL_ATTEMPTS, filter.scope)
        assertTrue(filter.isAll)
        assertTrue(filter.isEmpty)
    }

    @Test
    fun allFilterMatchesDefaultAndAllQuestions() {
        val filter = StatusFilter.ALL
        assertEquals(emptySet(), filter.statuses)
        assertEquals(StatusScope.ALL_ATTEMPTS, filter.scope)
        assertTrue(filter.isAll)
        assertTrue(filter.isEmpty)
    }

    @Test
    fun explicitAllStatusesMatchesAllQuestions() {
        val filter = StatusFilter(statuses = QuestionStatus.entries.toSet(), scope = StatusScope.LATEST_ATTEMPT)
        assertTrue(filter.isAll)
        assertFalse(filter.isEmpty)
    }

    @Test
    fun specificFilterProperties() {
        val filter = StatusFilter(statuses = setOf(QuestionStatus.ANSWERED_CORRECTLY), scope = StatusScope.ALL_ATTEMPTS)
        assertFalse(filter.isAll)
        assertFalse(filter.isEmpty)
    }

    @Test
    fun serializationAndDeserialization() {
        val single = StatusFilter(
            statuses = setOf(QuestionStatus.ANSWERED_INCORRECTLY),
            scope = StatusScope.ALL_ATTEMPTS
        )
        val serializedSingle = single.serialize()
        assertEquals("ANSWERED_INCORRECTLY:ALL_ATTEMPTS", serializedSingle)
        val deserializedSingle = StatusFilter.deserialize(serializedSingle)
        assertEquals(single, deserializedSingle)

        val multi = StatusFilter(
            statuses = setOf(QuestionStatus.NOT_YET_ANSWERED, QuestionStatus.ANSWERED_INCORRECTLY),
            scope = StatusScope.LATEST_ATTEMPT
        )
        val serializedMulti = multi.serialize()
        val deserializedMulti = StatusFilter.deserialize(serializedMulti)
        assertEquals(multi, deserializedMulti)
    }

    @Test
    fun deserializeEmptyOrAll() {
        assertEquals(StatusFilter.ALL, StatusFilter.deserialize(""))
        assertEquals(StatusFilter.ALL, StatusFilter.deserialize("ALL"))
        assertEquals(StatusFilter.ALL, StatusFilter.deserialize("   "))

        val result = StatusFilter.deserialize("NOT_YET_ANSWERED:LATEST_ATTEMPT")
        assertEquals(setOf(QuestionStatus.NOT_YET_ANSWERED), result.statuses)
        assertEquals(StatusScope.LATEST_ATTEMPT, result.scope)
    }
}
