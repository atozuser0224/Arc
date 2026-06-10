@file:JvmName("PerfUtils")

package dev.arc.api.perf

import java.util.concurrent.ConcurrentHashMap

/**
 * Allocation- and recompute-reduction primitives for hot paths - the cheap, unglamorous optimizations that
 * keep per-tick work off the GC and CPU.
 */

/**
 * A bounded, thread-safe object pool. Reuse short-lived scratch objects (vectors, buffers, builders) instead
 * of allocating per call, cutting GC pressure on hot paths.
 *
 * ```kotlin
 * val vectors = RecyclePool({ Vector() }, reset = { it.zero() })
 * vectors.borrowing { v -> v.setX(1.0); apply(v) }   // auto-returned
 * ```
 */
public class RecyclePool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    private val maxSize: Int = 128,
) {
    private val items = ArrayDeque<T>()

    /** Take an object from the pool, or create one if empty. */
    public fun borrow(): T = synchronized(items) {
        if (items.isEmpty()) factory() else items.removeLast()
    }

    /** Reset and return an object to the pool (dropped if the pool is at capacity). */
    public fun recycle(item: T) {
        reset(item)
        synchronized(items) {
            if (items.size < maxSize) items.addLast(item)
        }
    }

    /** Borrow for the duration of [block], guaranteeing the object is recycled afterwards. */
    public inline fun <R> borrowing(block: (T) -> R): R {
        val item = borrow()
        try {
            return block(item)
        } finally {
            recycle(item)
        }
    }
}

/**
 * A time-to-live memoizer: caches each key's computed value for [ttlMillis], collapsing repeated expensive
 * lookups (DB rows, permission checks, expensive derivations) within a window into one.
 */
public class TtlMemo<K : Any, V>(
    private val ttlMillis: Long,
    private val loader: (K) -> V,
) {
    private class Entry<V>(val value: V, val expireAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

    /** Get the cached value if fresh, else (re)compute, cache and return it. */
    public operator fun get(key: K): V {
        val now = System.currentTimeMillis()
        val cached = map[key]
        if (cached != null && cached.expireAt > now) return cached.value
        val value = loader(key)
        map[key] = Entry(value, now + ttlMillis)
        return value
    }

    /** Drop a single key. */
    public fun invalidate(key: K) {
        map.remove(key)
    }

    /** Drop everything. */
    public fun clear() {
        map.clear()
    }
}

/**
 * A minimal wall-clock rate limiter: runs the action at most once per [intervalMillis]. Use to throttle
 * expensive reactions (recomputing a scoreboard, saving data) that an event might fire far too often.
 */
public class RateLimiter(private val intervalMillis: Long) {

    @Volatile
    private var lastRun: Long = 0L

    /** Run [action] and return `true` if enough time has elapsed since the last run; otherwise skip and return `false`. */
    public fun tryRun(action: () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRun >= intervalMillis) {
            lastRun = now
            action()
            return true
        }
        return false
    }
}
