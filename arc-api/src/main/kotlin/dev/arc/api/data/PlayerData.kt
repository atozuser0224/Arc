@file:JvmName("PlayerData")

package dev.arc.api.data

import com.google.gson.Gson
import dev.arc.api.coroutine.launch
import dev.arc.api.event.listen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val gson = Gson()

@PublishedApi internal val registry = ConcurrentHashMap<Class<*>, ArcPlayerData<*>>()

/**
 * Type-safe per-player data backed by JSON files in `<dataFolder>/playerdata/<ClassName>/`.
 * Data is loaded from disk on join and saved on quit automatically.
 *
 * Register once, access from anywhere:
 * ```kotlin
 * // In onEnable:
 * plugin.registerPlayerData { CoinData() }
 *
 * // Anywhere:
 * val coins = player.data<CoinData>()
 * coins.amount += 100
 * ```
 */
public class ArcPlayerData<T : Any> @PublishedApi internal constructor(
    private val plugin: Plugin,
    private val clazz: Class<T>,
    private val default: () -> T,
) {
    private val cache = ConcurrentHashMap<UUID, T>()
    private val dataDir get() = File(plugin.dataFolder, "playerdata/${clazz.simpleName}")

    @PublishedApi internal fun get(uuid: UUID): T = cache.getOrPut(uuid, default)
    @PublishedApi internal fun set(uuid: UUID, value: T) { cache[uuid] = value }

    @PublishedApi internal suspend fun load(uuid: UUID) {
        val file = File(dataDir, "$uuid.json")
        if (!file.exists()) return
        val loaded = withContext(Dispatchers.IO) {
            runCatching { gson.fromJson(file.readText(), clazz) }.getOrNull()
        }
        if (loaded != null) cache[uuid] = loaded
    }

    @PublishedApi internal suspend fun save(uuid: UUID) {
        val value = cache.remove(uuid) ?: return
        withContext(Dispatchers.IO) {
            val dir = dataDir
            dir.mkdirs()
            File(dir, "$uuid.json").writeText(gson.toJson(value))
        }
    }

    /** Persist all cached players — call on plugin disable. */
    public suspend fun saveAll() {
        for (uuid in cache.keys.toList()) save(uuid)
    }
}

/**
 * Register [T] as a player data type owned by this plugin.
 * Auto-loads on [PlayerJoinEvent] and auto-saves on [PlayerQuitEvent].
 *
 * @param default factory for a fresh instance when no saved data exists.
 */
public inline fun <reified T : Any> Plugin.registerPlayerData(noinline default: () -> T): ArcPlayerData<T> {
    val store = ArcPlayerData(this, T::class.java, default)
    registry[T::class.java] = store
    listen<PlayerJoinEvent> { e -> launch { store.load(e.player.uniqueId) } }
    listen<PlayerQuitEvent> { e -> launch { store.save(e.player.uniqueId) } }
    return store
}

/**
 * Access the [T] data for this player.
 * Throws if [Plugin.registerPlayerData] was not called for [T].
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified T : Any> Player.data(): T {
    val store = registry[T::class.java] as? ArcPlayerData<T>
        ?: error("PlayerData<${T::class.simpleName}> not registered — call plugin.registerPlayerData<${T::class.simpleName}> in onEnable")
    return store.get(uniqueId)
}

/** Overwrite the [T] data for this player in-memory (persisted on next save). */
@Suppress("UNCHECKED_CAST")
public inline fun <reified T : Any> Player.setData(value: T) {
    val store = registry[T::class.java] as? ArcPlayerData<T>
        ?: error("PlayerData<${T::class.simpleName}> not registered")
    store.set(uniqueId, value)
}
