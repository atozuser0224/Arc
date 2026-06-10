package dev.arc.api.data

import dev.arc.api.coroutine.launch
import dev.arc.api.event.listen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A per-player data cache with optional async persistence. [get] always returns a value (creating a [default]
 * if absent); when [start]ed, it loads each player's data off-thread on join and saves it on quit, so plugin
 * code touches only the in-memory value on the main thread.
 *
 * ```kotlin
 * val coins = PlayerStore(plugin, default = { 0 },
 *     loader = { uuid -> sqlConnection(url) { it.query("...", uuid.toString()).firstOrNull()?.get("coins") as? Int } },
 *     saver  = { uuid, value -> sqlConnection(url) { it.update("...", value, uuid.toString()) } })
 * coins.start()
 * coins[player] = coins[player] + 10
 * ```
 */
public class PlayerStore<T : Any>(
    private val plugin: Plugin,
    private val default: (UUID) -> T,
    private val loader: (suspend (UUID) -> T?)? = null,
    private val saver: (suspend (UUID, T) -> Unit)? = null,
) {
    private val cache = ConcurrentHashMap<UUID, T>()

    /** The cached value for [player], creating a default if none is loaded yet. */
    public operator fun get(player: Player): T = cache.getOrPut(player.uniqueId) { default(player.uniqueId) }

    /** Overwrite the cached value for [player]. */
    public operator fun set(player: Player, value: T) {
        cache[player.uniqueId] = value
    }

    /** Begin auto load-on-join / save-on-quit. */
    public fun start() {
        val load = loader
        val save = saver
        plugin.listen<PlayerJoinEvent> { event ->
            if (load != null) {
                val uuid = event.player.uniqueId
                plugin.launch {
                    val loaded = withContext(Dispatchers.IO) { load(uuid) }
                    if (loaded != null) cache[uuid] = loaded
                }
            }
        }
        plugin.listen<PlayerQuitEvent> { event ->
            val uuid = event.player.uniqueId
            val value = cache.remove(uuid)
            if (value != null && save != null) {
                plugin.launch { withContext(Dispatchers.IO) { save(uuid, value) } }
            }
        }
    }

    /** Persist every cached value (e.g. on plugin disable). */
    public suspend fun saveAll() {
        val save = saver ?: return
        for ((uuid, value) in cache) {
            withContext(Dispatchers.IO) { save(uuid, value) }
        }
    }
}
