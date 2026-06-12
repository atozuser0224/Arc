@file:JvmName("PlayerCaches")

package dev.arc.api.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Per-player async cache with optional TTL and automatic eviction on disconnect.
 *
 * Avoids hitting a DB or doing expensive computation on every tick for the same player.
 *
 * ```kotlin
 * // Create once in your plugin:
 * val coinCache = plugin.playerCache(ttl = 1.minutes) { uuid ->
 *     db.query("SELECT coins FROM players WHERE uuid = ?", uuid.toString())
 *         .firstOrNull()?.get<Int>("coins") ?: 0
 * }
 *
 * // In a command:
 * val coins = coinCache.get(player)
 * player.sendMessage("You have $coins coins.")
 *
 * // After modifying the DB:
 * coinCache.invalidate(player.uniqueId)
 * ```
 */
class PlayerCache<T> internal constructor(
    plugin: Plugin,
    private val ttlMs: Long,
    private val loader: suspend (UUID) -> T,
) {
    private data class Entry<T>(val value: T, val loadedAt: Long)

    private val store = ConcurrentHashMap<UUID, Entry<T>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listener = object : Listener {
        @EventHandler
        fun on(e: PlayerQuitEvent) { store.remove(e.player.uniqueId) }
    }

    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }

    /** Unregister the auto-eviction listener (call on plugin disable). */
    fun dispose() = HandlerList.unregisterAll(listener)

    // ---- Access ----

    /**
     * Get the cached value for [uuid], loading it via the loader if absent or expired.
     * Always suspends on a cache miss.
     */
    suspend fun get(uuid: UUID): T {
        val entry = store[uuid]
        if (entry != null && (ttlMs <= 0 || System.currentTimeMillis() - entry.loadedAt < ttlMs)) {
            return entry.value
        }
        val value = loader(uuid)
        store[uuid] = Entry(value, System.currentTimeMillis())
        return value
    }

    /** Get the cached value for [player]. */
    suspend fun get(player: Player): T = get(player.uniqueId)

    /** Return the cached value synchronously without loading, or null if absent or expired. */
    fun cached(uuid: UUID): T? {
        val entry = store[uuid] ?: return null
        if (ttlMs > 0 && System.currentTimeMillis() - entry.loadedAt >= ttlMs) {
            store.remove(uuid)
            return null
        }
        return entry.value
    }

    /** Return the cached value for [player] synchronously, or null. */
    fun cached(player: Player): T? = cached(player.uniqueId)

    // ---- Mutation ----

    /**
     * Store [value] directly for [uuid] (e.g. after an update — avoids a reload round-trip).
     */
    fun put(uuid: UUID, value: T) {
        store[uuid] = Entry(value, System.currentTimeMillis())
    }

    /** Store [value] for [player]. */
    fun put(player: Player, value: T) = put(player.uniqueId, value)

    /** Remove the cached entry for [uuid] (forces a reload on next [get]). */
    fun invalidate(uuid: UUID) { store.remove(uuid) }

    /** Remove the cached entry for [player]. */
    fun invalidate(player: Player) = invalidate(player.uniqueId)

    /** Remove ALL cached entries. */
    fun invalidateAll() { store.clear() }

    /** Prefetch [uuid] in the background so the value is warm for the next [get]. */
    fun prefetch(uuid: UUID) { scope.launch { get(uuid) } }

    /** Prefetch [player] in the background. */
    fun prefetch(player: Player) = prefetch(player.uniqueId)

    /** Number of currently cached entries. */
    val size: Int get() = store.size
}

/**
 * Create a [PlayerCache] attached to this plugin.
 *
 * [ttl] controls how long a cached entry is valid (0 or [Duration.INFINITE] = forever).
 * [loader] is called on cache miss — runs on `Dispatchers.IO`.
 *
 * ```kotlin
 * val xpCache = plugin.playerCache<Int>(ttl = 2.minutes) { uuid ->
 *     database.loadXp(uuid)
 * }
 * ```
 */
fun <T> Plugin.playerCache(
    ttl: Duration = 5.minutes,
    loader: suspend (UUID) -> T,
): PlayerCache<T> {
    val ttlMs = if (ttl.isInfinite()) 0L else ttl.inWholeMilliseconds
    return PlayerCache(this, ttlMs, loader)
}
