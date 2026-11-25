package com.medqb.app.shared.data

/**
 * Normalizes a database name or filename by trimming whitespace and stripping a
 * trailing `.db` extension case-insensitively so all repositories, DAOs, and UI
 * state use identical database keys.
 */
fun canonicalizeDbName(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.endsWith(".db", ignoreCase = true)) {
        trimmed.dropLast(3).trim()
    } else {
        trimmed
    }
}
