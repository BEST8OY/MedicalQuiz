package com.medicalquiz.app.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: Observer<T>) {
    observe(owner, object : Observer<T> {
        // Use the same parameter name as the interface to avoid Kotlin warnings
        override fun onChanged(value: T) {
            observer.onChanged(value)
            removeObserver(this)
        }
    })
}

/**
 * Observe live data until the given predicate is true for a value — upon which the observer will be removed.
 * The observer will be called for each emitted value (including intermediate values); removal happens after the
 * predicate returns true for the current value.
 */
fun <T> LiveData<T>.observeUntil(owner: LifecycleOwner, stopPredicate: (T) -> Boolean, observer: Observer<T>) {
    observe(owner, object : Observer<T> {
        override fun onChanged(value: T) {
            observer.onChanged(value)
            if (stopPredicate(value)) removeObserver(this)
        }
    })
}
