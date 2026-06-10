package dev.arc.api.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight per-key cooldown tracker (e.g. ability/command rate-limits).
 * Uses wall-clock millis; entries are lazily overwritten, so no cleanup task is
 * needed for normal use.
 *
 * ```
 * val combat = Cooldown<UUID>(3_000)
 * if (combat.tryUse(player.uniqueId)) fireball() else player.send("<red>On cooldown")
 * ```
 */
class Cooldown<K>(private val durationMillis: Long) {

    private val until = ConcurrentHashMap<K, Long>()

    /** True if [key] is currently off cooldown (ready to use). */
    fun isReady(key: K): Boolean {
        val t = until[key] ?: return true
        return System.currentTimeMillis() >= t
    }

    /** If ready, arm the cooldown and return true; otherwise return false. */
    fun tryUse(key: K): Boolean {
        val now = System.currentTimeMillis()
        val t = until[key]
        if (t != null && now < t) return false
        until[key] = now + durationMillis
        return true
    }

    /** Milliseconds remaining before [key] is ready again (0 if ready). */
    fun remaining(key: K): Long {
        val t = until[key] ?: return 0
        return (t - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /** Clear the cooldown for [key]. */
    fun reset(key: K) {
        until.remove(key)
    }
}
