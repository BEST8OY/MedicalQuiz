package com.medicalquiz.app.shared.platform

/**
 * Platform-agnostic logging utility.
 * Uses expect/actual pattern to provide proper logging on each platform.
 */
expect object Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String)
}
