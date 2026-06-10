@file:JvmName("Nbt")

package dev.arc.api.nbt

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType

/**
 * Structured NBT-style access via Bukkit's PersistentDataContainer API.
 *
 * Wraps a [PersistentDataContainer] with a map-like interface.
 *
 * ```kotlin
 * val nbt = entity.nbt
 * nbt["arc:level"] = 5
 * val level: Int = nbt["arc:level"] ?: 0
 * nbt.remove("arc:level")
 * ```
 */
public class NbtMap internal constructor(
    public val container: PersistentDataContainer,
    private val plugin: String = "arc",
) {
    private fun key(name: String): NamespacedKey {
        return if (':' in name) NamespacedKey.fromString(name)!!
        else NamespacedKey(plugin, name)
    }

    public operator fun set(name: String, value: Int)     { container.set(key(name), PersistentDataType.INTEGER, value) }
    public operator fun set(name: String, value: Long)    { container.set(key(name), PersistentDataType.LONG, value) }
    public operator fun set(name: String, value: Double)  { container.set(key(name), PersistentDataType.DOUBLE, value) }
    public operator fun set(name: String, value: Float)   { container.set(key(name), PersistentDataType.FLOAT, value) }
    public operator fun set(name: String, value: String)  { container.set(key(name), PersistentDataType.STRING, value) }
    public operator fun set(name: String, value: Boolean) { container.set(key(name), PersistentDataType.BOOLEAN, value) }
    public operator fun set(name: String, value: ByteArray) { container.set(key(name), PersistentDataType.BYTE_ARRAY, value) }
    public operator fun set(name: String, value: IntArray)  { container.set(key(name), PersistentDataType.INTEGER_ARRAY, value) }
    public operator fun set(name: String, value: LongArray) { container.set(key(name), PersistentDataType.LONG_ARRAY, value) }

    @Suppress("UNCHECKED_CAST")
    public inline fun <reified T : Any> get(name: String): T? {
        val k = key(name)
        return when (T::class) {
            Int::class     -> container.get(k, PersistentDataType.INTEGER)
            Long::class    -> container.get(k, PersistentDataType.LONG)
            Double::class  -> container.get(k, PersistentDataType.DOUBLE)
            Float::class   -> container.get(k, PersistentDataType.FLOAT)
            String::class  -> container.get(k, PersistentDataType.STRING)
            Boolean::class -> container.get(k, PersistentDataType.BOOLEAN)
            ByteArray::class  -> container.get(k, PersistentDataType.BYTE_ARRAY)
            IntArray::class   -> container.get(k, PersistentDataType.INTEGER_ARRAY)
            LongArray::class  -> container.get(k, PersistentDataType.LONG_ARRAY)
            else -> null
        } as? T
    }

    public fun has(name: String): Boolean = container.keys.any { it.toString() == name || it.key == name }

    public fun remove(name: String) { container.remove(key(name)) }

    public fun keys(): Set<NamespacedKey> = container.keys
}

/** Access this holder's NBT data as an [NbtMap]. */
public val PersistentDataHolder.nbt: NbtMap
    get() = NbtMap(persistentDataContainer)

/** Access with a specific [plugin] namespace. */
public fun PersistentDataHolder.nbt(plugin: String): NbtMap =
    NbtMap(persistentDataContainer, plugin)
