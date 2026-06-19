package com.medqb.app.shared.ui.richtext.parser

/**
 * Object pool for StringBuilder instances to reduce memory allocations.
 * Thread-safe implementation using synchronized access.
 */
internal object StringBuilderPool {
    private const val MAX_POOL_SIZE = 8
    private val pool = ArrayDeque<StringBuilder>(MAX_POOL_SIZE)

    /**
     * Obtains a StringBuilder from the pool or creates a new one.
     *
     * @return A clean StringBuilder instance
     */
    fun obtain(): StringBuilder = synchronized(pool) {
        pool.removeLastOrNull()?.apply { setLength(0) } ?: StringBuilder()
    }

    /**
     * Returns a StringBuilder to the pool for reuse.
     *
     * @param builder The StringBuilder to recycle
     */
    fun recycle(builder: StringBuilder) {
        builder.setLength(0)
        synchronized(pool) {
            if (pool.size < MAX_POOL_SIZE) {
                pool.addLast(builder)
            }
        }
    }
}
