package com.medicalquiz.app.shared.ui.richtext

internal fun Set<String>.containsInsensitive(target: String): Boolean {
    return any { it.equals(target, ignoreCase = true) }
}

internal fun Set<String>.containsAnyInsensitive(targets: Set<String>): Boolean {
    if (isEmpty() || targets.isEmpty()) return false
    return targets.any { target -> containsInsensitive(target) }
}

internal fun Set<String>.matchesAnyMarker(markers: Set<String>): Boolean {
    if (isEmpty() || markers.isEmpty()) return false
    return any { candidate -> markers.contains(normalizeMarker(candidate)) }
}

internal fun normalizeMarker(value: String): String {
    if (value.isEmpty()) return value
    val sb = StringBuilder(value.length)
    value.forEach { char ->
        if (!char.isWhitespace() && char != '-' && char != '_') {
            sb.append(char.lowercaseChar())
        }
    }
    return sb.toString()
}

internal fun normalizedMarkers(vararg markers: String): Set<String> {
    if (markers.isEmpty()) return emptySet()
    return markers.mapTo(mutableSetOf()) { marker -> normalizeMarker(marker) }
}
