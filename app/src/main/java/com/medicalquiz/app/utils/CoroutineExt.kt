package com.medicalquiz.app.utils

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun <T> LifecycleOwner.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: suspend CoroutineScope.() -> T,
    onSuccess: suspend CoroutineScope.(T) -> Unit = {},
    onFailure: suspend CoroutineScope.(Throwable) -> Unit = {}
) {
    lifecycleScope.launch(dispatcher) {
        runCatching { block() }
            .onSuccess { onSuccess(it) }
            .onFailure { onFailure(it) }
    }
}

fun <T> LifecycleCoroutineScope.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: suspend CoroutineScope.() -> T,
    onSuccess: suspend CoroutineScope.(T) -> Unit = {},
    onFailure: suspend CoroutineScope.(Throwable) -> Unit = {}
) {
    launch(dispatcher) {
        runCatching { block() }
            .onSuccess { onSuccess(it) }
            .onFailure { onFailure(it) }
    }
}
