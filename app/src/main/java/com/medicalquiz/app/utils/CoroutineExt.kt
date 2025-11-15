package com.medicalquiz.app.utils

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Launches a coroutine with structured error handling and performance optimizations.
 * Success and failure callbacks are always executed on the main thread.
 *
 * @param dispatcher The dispatcher to run the block on (default: Dispatchers.IO for I/O, Dispatchers.Default for computation)
 * @param timeout Optional timeout duration
 * @param block The suspend function to execute
 * @param onSuccess Callback executed on main thread when block succeeds
 * @param onFailure Callback executed on main thread when block fails (default: logs error)
 * @return The launched Job for cancellation support
 */
fun <T> LifecycleOwner.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.IO, // Better default for most operations
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        try {
            val result = if (timeout != null) {
                withTimeout(timeout) {
                    withContext(dispatcher) { block() }
                }
            } else {
                withContext(dispatcher) { block() }
            }
            onSuccess(result)
        } catch (e: TimeoutCancellationException) {
            onFailure(RuntimeException("Operation timed out after ${timeout?.inWholeSeconds}s", e))
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}

/**
 * Launches a coroutine with retry logic for transient failures.
 *
 * @param dispatcher The dispatcher to run the block on
 * @param maxRetries Maximum number of retry attempts
 * @param initialDelay Initial delay before first retry
 * @param backoffFactor Multiplier for delay between retries
 * @param timeout Optional timeout for each attempt
 * @param shouldRetry Predicate to determine if an exception should be retried
 * @param block The suspend function to execute
 * @param onSuccess Callback executed on main thread when block succeeds
 * @param onFailure Callback executed on main thread when all retries fail
 * @return The launched Job for cancellation support
 */
fun <T> LifecycleOwner.launchCatchingWithRetry(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxRetries: Int = 3,
    initialDelay: Duration = 1.seconds,
    backoffFactor: Double = 2.0,
    timeout: Duration? = null,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        var lastException: Throwable? = null
        var currentDelay = initialDelay

        for (attempt in 0..maxRetries) {
            try {
                val result = if (timeout != null) {
                    withTimeout(timeout) {
                        withContext(dispatcher) { block() }
                    }
                } else {
                    withContext(dispatcher) { block() }
                }
                onSuccess(result)
                return@launch
            } catch (e: TimeoutCancellationException) {
                lastException = RuntimeException("Operation timed out after ${timeout?.inWholeSeconds}s", e)
                if (attempt == maxRetries || !shouldRetry(e)) break
            } catch (e: Exception) {
                lastException = e
                if (attempt == maxRetries || !shouldRetry(e)) break

                // Wait before retry with exponential backoff
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * backoffFactor).coerceAtMost(30.seconds)
            }
        }

        onFailure(lastException ?: RuntimeException("Unknown error after $maxRetries retries"))
    }
}

/**
 * Launches a coroutine with structured error handling.
 * Success and failure callbacks are always executed on the main thread.
 *
 * @param dispatcher The dispatcher to run the block on (default: Dispatchers.IO)
 * @param timeout Optional timeout duration
 * @param block The suspend function to execute
 * @param onSuccess Callback executed on main thread when block succeeds
 * @param onFailure Callback executed on main thread when block fails (default: logs error)
 * @return The launched Job for cancellation support
 */
fun <T> LifecycleCoroutineScope.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.IO, // Better default
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return launch(Dispatchers.Main) {
        try {
            val result = if (timeout != null) {
                withTimeout(timeout) {
                    withContext(dispatcher) { block() }
                }
            } else {
                withContext(dispatcher) { block() }
            }
            onSuccess(result)
        } catch (e: TimeoutCancellationException) {
            onFailure(RuntimeException("Operation timed out after ${timeout?.inWholeSeconds}s", e))
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}
