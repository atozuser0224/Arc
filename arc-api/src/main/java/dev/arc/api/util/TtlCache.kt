package dev.arc.api.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Time-to-live memoization cache. Computes a value via [loader] and reuses it
 * until [ttlMillis] elapses — useful for throttling expensive per-tick lookups
 * (nearest player, region scans, web/db results) down to once per interval.
 *
 * ```
 * val nearby = TtlCache<Chunk, Int>(ttlMillis = 1000) { it.entities.size }
 * val count = nearby.get(chunk)   // recomputed at most once per second
 * ```
 *
 * Thread-safe storage; [loader] may run more than once for a cold key under
 * contention, so keep it side-effect free.
 */
class TtlCache<K, V>(
    private val ttlMillis: Long,
    private val loader: (K) -> V,
) {
    private class Entry<V>(val value: V, val expiresAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

    /** Cached value for [key], recomputed only if missing or expired. */
    fun get(key: K): V {
        val now = System.currentTimeMillis()
        val existing = map[key]
        if (existing != null && now < existing.expiresAt) return existing.value
        val value = loader(key)
        map[key] = Entry(value, now + ttlMillis)
        return value
    }

    /** Drop the cached value for [key], forcing a recompute next [get]. */
    fun invalidate(key: K) {
        map.remove(key)
    }

    /** Drop everything. */
    fun clear() {
        map.clear()
    }

    companion object {
        /**
         * Build a cache using the current default TTL from
         * [dev.arc.api.Arc.settings] (`defaultTtlMillis`).
         */
        fun <K, V> withDefaults(loader: (K) -> V): TtlCache<K, V> =
            TtlCache(dev.arc.api.Arc.settings.defaultTtlMillis, loader)
    }
}
