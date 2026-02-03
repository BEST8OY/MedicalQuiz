package com.medicalquiz.app.shared.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object Logger {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    actual fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        log("ERROR", tag, message)
        throwable?.printStackTrace()
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        log("WARN", tag, message)
        throwable?.printStackTrace()
    }

    actual fun i(tag: String, message: String) {
        log("INFO", tag, message)
    }

    private fun log(level: String, tag: String, message: String) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        println("[$timestamp] $level/$tag: $message")
    }
}
