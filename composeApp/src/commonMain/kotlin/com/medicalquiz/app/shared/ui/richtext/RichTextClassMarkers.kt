package com.medicalquiz.app.shared.ui.richtext

/**
 * Checks if this set contains a string matching the target (case-insensitive).
 * 
 * @param target The string to search for
 * @return true if a match is found
 */
internal fun Set<String>.containsInsensitive(target: String): Boolean {
    return any { it.equals(target, ignoreCase = true) }
}

/**
 * Checks if this set contains any of the target strings (case-insensitive).
 * 
 * @param targets The set of strings to search for
 * @return true if any match is found
 */
internal fun Set<String>.containsAnyInsensitive(targets: Set<String>): Boolean {
    if (isEmpty() || targets.isEmpty()) return false
    return targets.any { target -> containsInsensitive(target) }
}

/**
 * Checks if this set contains any normalized marker matching the provided markers.
 * 
 * @param markers The set of normalized markers to match against
 * @return true if any normalized marker matches
 */
internal fun Set<String>.matchesAnyMarker(markers: Set<String>): Boolean {
    if (isEmpty() || markers.isEmpty()) return false
    return any { candidate -> markers.contains(normalizeMarker(candidate)) }
}

/**
 * Normalizes a class marker by removing whitespace, hyphens, and underscores,
 * then converting to lowercase.
 * 
 * @param value The raw class marker string
 * @return Normalized marker string
 */
internal fun normalizeMarker(value: String): String {
    if (value.isEmpty()) return value
    return value.filter { !it.isWhitespace() && it != '-' && it != '_' }
        .lowercase()
}

/**
 * Creates a set of normalized markers from the provided marker strings.
 * 
 * @param markers Variable number of marker strings to normalize
 * @return Set of normalized marker strings
 */
internal fun normalizedMarkers(vararg markers: String): Set<String> {
    if (markers.isEmpty()) return emptySet()
    return markers.mapTo(mutableSetOf()) { marker -> normalizeMarker(marker) }
}
