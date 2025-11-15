package com.medicalquiz.app.utils

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launches a coroutine with structured error handling.
 * Success and failure callbacks are always executed on the main thread.
 *
 * @param dispatcher The dispatcher to run the block on (default: Dispatchers.Default)
 * @param block The suspend function to execute
 * @param onSuccess Callback executed on main thread when block succeeds
 * @param onFailure Callback executed on main thread when block fails (default: logs error)
 * @return The launched Job for cancellation support
 */
fun <T> LifecycleOwner.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return lifecycleScope.launch(Dispatchers.Main) {
        try {
            val result = withContext(dispatcher) { block() }
            onSuccess(result)
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}

/**
 * Launches a coroutine with structured error handling.
 * Success and failure callbacks are always executed on the main thread.
 *
 * @param dispatcher The dispatcher to run the block on (default: Dispatchers.Default)
 * @param block The suspend function to execute
 * @param onSuccess Callback executed on main thread when block succeeds
 * @param onFailure Callback executed on main thread when block fails (default: logs error)
 * @return The launched Job for cancellation support
 */
fun <T> LifecycleCoroutineScope.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend CoroutineScope.() -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = { it.printStackTrace() }
): Job {
    return launch(Dispatchers.Main) {
        try {
            val result = withContext(dispatcher) { block() }
            onSuccess(result)
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}
