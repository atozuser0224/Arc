@file:JvmName("WorldData")

package dev.arc.api.world

import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * Per-world persistent key-value store backed by [World.getPersistentDataContainer].
 *
 * Data survives server restarts.  Keys are namespaced under your plugin to avoid conflicts.
 * All primitive Bukkit [PersistentDataType]s are supported via typed get/set overloads.
 *
 * ```kotlin
 * val store = WorldDataStore(plugin, world)
 *
 * store.setString("phase", "night_hunt")
 * store.setInt("wave", 3)
 *
 * val phase: String? = store.getString("phase")
 * val wave:  Int?    = store.getInt("wave")
 *
 * store.remove("phase")
 * ```
 */
class WorldDataStore(private val plugin: Plugin, val world: World) {

    private fun key(name: String) = NamespacedKey(plugin, name)

    fun getString(name: String): String? = world.persistentDataContainer.get(key(name), PersistentDataType.STRING)
    fun setString(name: String, value: String) { world.persistentDataContainer.set(key(name), PersistentDataType.STRING, value) }

    fun getInt(name: String): Int? = world.persistentDataContainer.get(key(name), PersistentDataType.INTEGER)
    fun setInt(name: String, value: Int) { world.persistentDataContainer.set(key(name), PersistentDataType.INTEGER, value) }

    fun getLong(name: String): Long? = world.persistentDataContainer.get(key(name), PersistentDataType.LONG)
    fun setLong(name: String, value: Long) { world.persistentDataContainer.set(key(name), PersistentDataType.LONG, value) }

    fun getDouble(name: String): Double? = world.persistentDataContainer.get(key(name), PersistentDataType.DOUBLE)
    fun setDouble(name: String, value: Double) { world.persistentDataContainer.set(key(name), PersistentDataType.DOUBLE, value) }

    fun getBoolean(name: String): Boolean? = world.persistentDataContainer.get(key(name), PersistentDataType.BOOLEAN)
    fun setBoolean(name: String, value: Boolean) { world.persistentDataContainer.set(key(name), PersistentDataType.BOOLEAN, value) }

    fun getByteArray(name: String): ByteArray? = world.persistentDataContainer.get(key(name), PersistentDataType.BYTE_ARRAY)
    fun setByteArray(name: String, value: ByteArray) { world.persistentDataContainer.set(key(name), PersistentDataType.BYTE_ARRAY, value) }

    fun has(name: String): Boolean = world.persistentDataContainer.has(key(name))
    fun remove(name: String) { world.persistentDataContainer.remove(key(name)) }

    /** Returns all key names set by this plugin on this world. */
    fun keys(): Set<String> = world.persistentDataContainer.keys
        .filter { it.namespace() == plugin.name.lowercase() }
        .map { it.key() }
        .toSet()
}

/** Create a [WorldDataStore] for this world scoped to [plugin]. */
fun World.dataStore(plugin: Plugin): WorldDataStore = WorldDataStore(plugin, this)
