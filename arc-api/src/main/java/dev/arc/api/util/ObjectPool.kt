package dev.arc.api.util

import java.util.ArrayDeque

/**
 * Small bounded object pool to cut allocation / GC pressure in hot paths
 * (reusable scratch buffers, builders, math temporaries). Reused instances are
 * passed through [reset] before being handed out again.
 *
 * ```
 * val buffers = ObjectPool(maxSize = 64, factory = { StringBuilder() }, reset = { it.setLength(0) })
 * buffers.use { sb -> sb.append(...).toString() }
 * ```
 *
 * Not synchronized — intended for single-thread (main-thread) hot loops. Wrap
 * externally if shared across threads.
 */
class ObjectPool<T>(
    private val maxSize: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
) {
    private val pool = ArrayDeque<T>()

    /** Borrow an instance — reused if one is pooled, otherwise freshly built. */
    fun acquire(): T = pool.pollLast() ?: factory()

    /** Return an instance to the pool (dropped if the pool is already full). */
    fun release(item: T) {
        reset(item)
        if (pool.size < maxSize) pool.addLast(item)
    }

    /** Borrow for the duration of [block], always returning the instance after. */
    inline fun <R> use(block: (T) -> R): R {
        val item = acquire()
        try {
            return block(item)
        } finally {
            release(item)
        }
    }

    companion object {
        /**
         * Build a pool sized from the current default in [dev.arc.api.Arc.settings]
         * (`defaultPoolSize`).
         */
        fun <T> withDefaults(factory: () -> T, reset: (T) -> Unit = {}): ObjectPool<T> =
            ObjectPool(dev.arc.api.Arc.settings.defaultPoolSize, factory, reset)
    }
}
