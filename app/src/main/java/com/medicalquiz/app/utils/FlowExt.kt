package com.medicalquiz.app.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

/**
 * Returns the first value that satisfies [predicate]. This mirrors the familiar `first` operator, but
 * provides a clearer name aligned with our legacy LiveData helper.
 */
suspend fun <T> Flow<T>.firstMatching(predicate: (T) -> Boolean = { true }): T = first(predicate)

/**
 * Backwards-compatible alias from older code that used `firstValue`.
 */
suspend fun <T> Flow<T>.firstValue(predicate: (T) -> Boolean = { true }): T = firstMatching(predicate)

/**
 * Collect values until [stopPredicate] returns true for the emitted value — includes the value that triggers the stop.
 * Mirrors LiveData.observeUntil semantics.
 */
/**
 * Collect activity until [stopPredicate] returns true for a value — includes the value that triggers
 * the stop. This mirrors the behavior of LiveData.observeUntil but for Flow.
 */
suspend fun <T> Flow<T>.collectUntil(stopPredicate: (T) -> Boolean, collector: suspend (T) -> Unit) {
    collect { value ->
        collector(value)
        if (stopPredicate(value)) return@collect
    }
}
