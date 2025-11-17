package com.medicalquiz.app.utils

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Coroutine utilities providing structured error handling, timeout support, and retry logic.
 * All callbacks execute on the main thread for safe UI updates.
 */

// ============================================================================
// Constants
// ============================================================================

private const val DEFAULT_MAX_RETRIES = 3
private val DEFAULT_INITIAL_DELAY = 1.seconds
private const val DEFAULT_BACKOFF_FACTOR = 2.0
private val MAX_RETRY_DELAY = 30.seconds

// ============================================================================
// LifecycleOwner Extensions
// ============================================================================

/**
 * Launches a coroutine with structured error handling and optional timeout.
 * 
 * Executes the block on the specified dispatcher and handles success/failure
 * cases with callbacks that run on the main thread for safe UI updates.
 *
 * @param dispatcher The dispatcher to execute the block on (default: IO for I/O operations)
 * @param timeout Optional timeout duration for the operation
 * @param block The suspend function to execute
 * @param onSuccess Callback invoked on main thread when block succeeds
 * @param onFailure Callback invoked on main thread when block fails
 * @return Job that can be used to cancel the operation
 *
 * @sample
 * ```
 * launchCatching(
 *     timeout = 5.seconds,
 *     block = { repository.fetchData() },
 *     onSuccess = { data -> updateUI(data) },
 *     onFailure = { error -> showError(error.message) }
 * )
 * ```
 */
fun <T> LifecycleOwner.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        executeCatchingBlock(
            dispatcher = dispatcher,
            timeout = timeout,
            block = block,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}

/**
 * Launches a coroutine with retry logic for handling transient failures.
 * 
 * Automatically retries failed operations with exponential backoff between attempts.
 * Useful for network operations or database queries that may fail temporarily.
 *
 * Executes with total attempts = 1 (initial) + maxRetries. For example, maxRetries=3
 * means 1 initial attempt + 3 retries = 4 total attempts.
 *
 * Note: If the coroutine is cancelled during retry delay (e.g., lifecycle destroyed),
 * the CancellationException will propagate and onFailure will NOT be called. This
 * follows standard coroutine cancellation semantics.
 *
 * @param dispatcher The dispatcher to execute the block on
 * @param maxRetries Maximum number of retry attempts after initial attempt (default: 3)
 * @param initialDelay Initial delay before first retry (default: 1 second)
 * @param backoffFactor Multiplier for delay between retries (default: 2.0)
 * @param timeout Optional timeout for each individual attempt
 * @param shouldRetry Predicate to determine if exception should trigger retry
 * @param block The suspend function to execute
 * @param onSuccess Callback invoked on main thread when block succeeds
 * @param onFailure Callback invoked on main thread when all retries fail (not called if cancelled)
 * @return Job that can be used to cancel the operation
 *
 * @sample
 * ```
 * launchCatchingWithRetry(
 *     maxRetries = 3,
 *     shouldRetry = { it is IOException },
 *     block = { apiService.getData() },
 *     onSuccess = { data -> displayData(data) },
 *     onFailure = { error -> showRetryDialog(error) }
 * )
 * ```
 */
fun <T> LifecycleOwner.launchCatchingWithRetry(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
    initialDelay: Duration = DEFAULT_INITIAL_DELAY,
    backoffFactor: Double = DEFAULT_BACKOFF_FACTOR,
    timeout: Duration? = null,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        executeWithRetry(
            dispatcher = dispatcher,
            maxRetries = maxRetries,
            initialDelay = initialDelay,
            backoffFactor = backoffFactor,
            timeout = timeout,
            shouldRetry = shouldRetry,
            block = block,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}

// ============================================================================
// LifecycleCoroutineScope Extensions
// ============================================================================

/**
 * Launches a coroutine with structured error handling and optional timeout.
 * 
 * Similar to LifecycleOwner.launchCatching but operates directly on a
 * LifecycleCoroutineScope for more granular control.
 *
 * @param dispatcher The dispatcher to execute the block on
 * @param timeout Optional timeout duration for the operation
 * @param block The suspend function to execute
 * @param onSuccess Callback invoked on main thread when block succeeds
 * @param onFailure Callback invoked on main thread when block fails
 * @return Job that can be used to cancel the operation
 */
fun <T> LifecycleCoroutineScope.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    timeout: Duration? = null,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return launch(Dispatchers.Main) {
        executeCatchingBlock(
            dispatcher = dispatcher,
            timeout = timeout,
            block = block,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}

// ============================================================================
// Core Execution Logic
// ============================================================================

/**
 * Executes a block with error handling and optional timeout.
 * Must be called from a coroutine context on the Main dispatcher.
 * 
 * Important: CancellationException is re-thrown to preserve coroutine
 * cancellation semantics. This ensures proper cleanup when the lifecycle ends.
 */
private suspend fun <T> executeCatchingBlock(
    dispatcher: CoroutineDispatcher,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit,
    onFailure: (Throwable) -> Unit
) {
    try {
        val result = executeWithOptionalTimeout(dispatcher, timeout, block)
        onSuccess(result)
    } catch (e: TimeoutCancellationException) {
        val timeoutError = createTimeoutException(timeout, e)
        onFailure(timeoutError)
    } catch (e: CancellationException) {
        throw e  // Re-throw to preserve cancellation semantics
    } catch (e: Exception) {
        onFailure(e)
    }
}

/**
 * Executes a block with retry logic and exponential backoff.
 * Must be called from a coroutine context on the Main dispatcher.
 * 
 * Total attempts = 1 (initial) + maxRetries. For example, with maxRetries=3,
 * there will be 1 initial attempt followed by up to 3 retries (4 attempts total).
 */
private suspend fun <T> executeWithRetry(
    dispatcher: CoroutineDispatcher,
    maxRetries: Int,
    initialDelay: Duration,
    backoffFactor: Double,
    timeout: Duration?,
    shouldRetry: (Throwable) -> Boolean,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit,
    onFailure: (Throwable) -> Unit
) {
    var lastException: Throwable? = null
    var currentDelay = initialDelay

    // Attempt initial + up to maxRetries times
    repeat(maxRetries + 1) { attempt ->
        val attemptResult = attemptExecution(
            dispatcher = dispatcher,
            timeout = timeout,
            block = block,
            currentAttempt = attempt,
            maxRetries = maxRetries,
            shouldRetry = shouldRetry
        )

        when (attemptResult) {
            is AttemptResult.Success -> {
                onSuccess(attemptResult.value)
                return
            }
            is AttemptResult.Failure -> {
                lastException = attemptResult.exception
                if (attemptResult.shouldStop) {
                    onFailure(lastException!!)
                    return
                }
            }
            is AttemptResult.Retry -> {
                lastException = attemptResult.exception
                delay(currentDelay)
                currentDelay = calculateNextDelay(currentDelay, backoffFactor)
            }
        }
    }

    // This should never be reached if logic is correct
    val finalException = lastException 
        ?: RuntimeException("Unknown error after $maxRetries retries")
    onFailure(finalException)
}

/**
 * Attempts to execute the block once, handling various failure scenarios.
 * 
 * Correctly determines when to stop retrying by checking if we've exhausted
 * our total attempts (initial + maxRetries).
 */
private suspend fun <T> attemptExecution(
    dispatcher: CoroutineDispatcher,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> T,
    currentAttempt: Int,
    maxRetries: Int,
    shouldRetry: (Throwable) -> Boolean
): AttemptResult<T> {
    return try {
        val result = executeWithOptionalTimeout(dispatcher, timeout, block)
        AttemptResult.Success(result)
    } catch (e: TimeoutCancellationException) {
        handleAttemptException(
            exception = e,
            currentAttempt = currentAttempt,
            maxRetries = maxRetries,
            shouldRetry = shouldRetry,
            isTimeout = true,
            timeout = timeout
        )
    } catch (e: Exception) {
        handleAttemptException(
            exception = e,
            currentAttempt = currentAttempt,
            maxRetries = maxRetries,
            shouldRetry = shouldRetry,
            isTimeout = false
        )
    }
}

/**
 * Determines the appropriate result based on the exception and retry conditions.
 * 
 * Uses > (not >=) for comparison because currentAttempt is 0-indexed:
 * - maxRetries=3 means attempts 0,1,2,3 (4 total)
 * - When currentAttempt=3, we've used all retries, so 3 > 2 is true (stop)
 */
private fun <T> handleAttemptException(
    exception: Exception,
    currentAttempt: Int,
    maxRetries: Int,
    shouldRetry: (Throwable) -> Boolean,
    isTimeout: Boolean,
    timeout: Duration? = null
): AttemptResult<T> {
    val wrappedException = if (isTimeout) {
        createTimeoutException(timeout, exception)
    } else {
        exception
    }

    return when {
        currentAttempt > maxRetries -> AttemptResult.Failure(wrappedException, shouldStop = true)
        !shouldRetry(exception) -> AttemptResult.Failure(wrappedException, shouldStop = true)
        else -> AttemptResult.Retry(wrappedException)
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Executes a block with optional timeout on the specified dispatcher.
 */
private suspend fun <T> executeWithOptionalTimeout(
    dispatcher: CoroutineDispatcher,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> T
): T {
    return if (timeout != null) {
        withTimeout(timeout) {
            withContext(dispatcher) { block() }
        }
    } else {
        withContext(dispatcher) { block() }
    }
}

/**
 * Creates a user-friendly timeout exception with context.
 */
private fun createTimeoutException(timeout: Duration?, cause: Throwable): RuntimeException {
    val timeoutSeconds = timeout?.inWholeSeconds ?: 0
    return RuntimeException("Operation timed out after ${timeoutSeconds}s", cause)
}

/**
 * Calculates the next retry delay using exponential backoff, capped at maximum.
 * 
 * Checks for overflow before multiplication to prevent Duration overflow
 * when backoff factor is large or currentDelay is already significant.
 */
private fun calculateNextDelay(currentDelay: Duration, backoffFactor: Double): Duration {
    val maxBeforeMultiply = MAX_RETRY_DELAY / backoffFactor
    return if (currentDelay >= maxBeforeMultiply) {
        // Multiplying would exceed max, so just return max
        MAX_RETRY_DELAY
    } else {
        (currentDelay * backoffFactor).coerceAtMost(MAX_RETRY_DELAY)
    }
}

// ============================================================================
// Attempt Result Sealed Class
// ============================================================================

/**
 * Represents the result of a single execution attempt.
 */
private sealed class AttemptResult<out T> {
    /** Execution succeeded with the given value */
    data class Success<T>(val value: T) : AttemptResult<T>()
    
    /** Execution failed and should retry */
    data class Retry(val exception: Throwable) : AttemptResult<Nothing>()
    
    /** Execution failed and should stop (no more retries) */
    data class Failure(val exception: Throwable, val shouldStop: Boolean) : AttemptResult<Nothing>()
}