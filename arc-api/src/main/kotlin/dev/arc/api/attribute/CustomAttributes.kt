@file:JvmName("CustomAttributes")

package dev.arc.api.attribute

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for custom numeric attributes on any [Entity], backed by PDC.
 *
 * Unlike vanilla Bukkit [Attribute]s, these are stored per-entity in persistent data
 * and survive restarts.  Every attribute has a default value and optional min/max clamp.
 *
 * ```kotlin
 * // Define attributes once (e.g., in your plugin's companion object)
 * val MANA         = CustomAttributeRegistry.define(plugin, "mana",         default = 100.0, min = 0.0, max = 200.0)
 * val SPEED_BONUS  = CustomAttributeRegistry.define(plugin, "speed_bonus",  default = 0.0)
 *
 * // Read / write
 * val mana = player.getCustomAttribute(MANA)      // 100.0 on first access
 * player.setCustomAttribute(MANA, mana - 30.0)
 * player.modifyCustomAttribute(MANA) { it + 5.0 } // atomic read-modify-write
 * ```
 */
object CustomAttributeRegistry {

    private val definitions = ConcurrentHashMap<String, CustomAttributeDef>()

    /**
     * Define a custom attribute identified by [name] under [plugin]'s namespace.
     * Re-defining the same name returns the existing definition.
     */
    fun define(
        plugin: Plugin,
        name: String,
        default: Double = 0.0,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
    ): CustomAttributeDef {
        val key = NamespacedKey(plugin, "attr_$name")
        return definitions.getOrPut(key.toString()) { CustomAttributeDef(key, default, min, max) }
    }

    /** All registered attribute definitions. */
    fun all(): Collection<CustomAttributeDef> = definitions.values
}

/** Definition of a custom attribute. Holds its key, default, and clamp bounds. */
class CustomAttributeDef internal constructor(
    val key: NamespacedKey,
    val default: Double,
    val min: Double,
    val max: Double,
)

/** Read a custom attribute from this entity (returns [def].default if not set). */
fun Entity.getCustomAttribute(def: CustomAttributeDef): Double =
    persistentDataContainer.get(def.key, PersistentDataType.DOUBLE) ?: def.default

/** Write a custom attribute value, clamped to the definition's [min]..[max]. */
fun Entity.setCustomAttribute(def: CustomAttributeDef, value: Double) {
    persistentDataContainer.set(def.key, PersistentDataType.DOUBLE, value.coerceIn(def.min, def.max))
}

/** Read, transform, and write a custom attribute atomically. */
fun Entity.modifyCustomAttribute(def: CustomAttributeDef, transform: (Double) -> Double) {
    setCustomAttribute(def, transform(getCustomAttribute(def)))
}

/** Remove a custom attribute from this entity (resets to default on next read). */
fun Entity.removeCustomAttribute(def: CustomAttributeDef) {
    persistentDataContainer.remove(def.key)
}
