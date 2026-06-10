@file:JvmName("PersistentData")

package dev.arc.api.pdc

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * Typed convenience over [org.bukkit.persistence.PersistentDataContainer] (PDC) - the supported way to stash
 * custom data on items, entities, tiles and chunks across restarts.
 *
 * The raw API forces a [PersistentDataType] token on every call and exposes only the container, not the
 * holder. These helpers operate directly on any [PersistentDataHolder] (Player, ItemMeta, Entity, ...), pick
 * the right type token for you, and model booleans (which PDC lacks natively) as a byte.
 *
 * ```kotlin
 * val key = plugin.key("souls")
 * player.setInt(key, player.getInt(key, default = 0) + 1)
 * ```
 */

/** Build a plugin-namespaced [NamespacedKey] (lower-cased). */
public fun Plugin.key(value: String): NamespacedKey = NamespacedKey(this, value)

public fun PersistentDataHolder.setString(key: NamespacedKey, value: String) {
    persistentDataContainer.set(key, PersistentDataType.STRING, value)
}

public fun PersistentDataHolder.getString(key: NamespacedKey, default: String? = null): String? =
    persistentDataContainer.get(key, PersistentDataType.STRING) ?: default

public fun PersistentDataHolder.setInt(key: NamespacedKey, value: Int) {
    persistentDataContainer.set(key, PersistentDataType.INTEGER, value)
}

public fun PersistentDataHolder.getInt(key: NamespacedKey, default: Int = 0): Int =
    persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: default

public fun PersistentDataHolder.setLong(key: NamespacedKey, value: Long) {
    persistentDataContainer.set(key, PersistentDataType.LONG, value)
}

public fun PersistentDataHolder.getLong(key: NamespacedKey, default: Long = 0L): Long =
    persistentDataContainer.get(key, PersistentDataType.LONG) ?: default

public fun PersistentDataHolder.setDouble(key: NamespacedKey, value: Double) {
    persistentDataContainer.set(key, PersistentDataType.DOUBLE, value)
}

public fun PersistentDataHolder.getDouble(key: NamespacedKey, default: Double = 0.0): Double =
    persistentDataContainer.get(key, PersistentDataType.DOUBLE) ?: default

public fun PersistentDataHolder.setBoolean(key: NamespacedKey, value: Boolean) {
    persistentDataContainer.set(key, PersistentDataType.BYTE, if (value) 1.toByte() else 0.toByte())
}

public fun PersistentDataHolder.getBoolean(key: NamespacedKey, default: Boolean = false): Boolean {
    val raw = persistentDataContainer.get(key, PersistentDataType.BYTE) ?: return default
    return raw.toInt() != 0
}

/** `true` if [key] is present in this holder's container. */
public fun PersistentDataHolder.hasKey(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key)

/** Remove [key] from this holder's container if present. */
public fun PersistentDataHolder.removeKey(key: NamespacedKey) {
    persistentDataContainer.remove(key)
}
