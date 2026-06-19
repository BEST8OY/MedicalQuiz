package com.medqb.app.shared.platform

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Singleton for managing VLC discovery to avoid repeated discovery attempts.
 * Caches the discovery result and provides retry capability.
 */
object VlcDiscovery {
    private var cachedResult: Boolean? = null
    private var lastError: String? = null

    /**
     * Checks if VLC is available, using cached result if available.
     * @return true if VLC is available, false otherwise
     */
    fun isAvailable(): Boolean {
        return cachedResult ?: run {
            cachedResult = try {
                NativeDiscovery().discover()
            } catch (e: Exception) {
                lastError = e.message
                false
            }
            cachedResult!!
        }
    }

    /**
     * Retries VLC discovery, clearing the cache.
     * @return true if VLC is now available, false otherwise
     */
    fun retry(): Boolean {
        cachedResult = null
        lastError = null
        return isAvailable()
    }

    /**
     * Gets the last error message if discovery failed.
     * @return error message or null if no error occurred
     */
    fun getLastError(): String? = lastError

    /**
     * Resets the discovery cache.
     */
    fun reset() {
        cachedResult = null
        lastError = null
    }
}
