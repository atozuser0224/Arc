@file:JvmName("Attributes")

package dev.arc.api.attribute

import org.bukkit.attribute.Attributable
import org.bukkit.attribute.Attribute

/**
 * Attribute helpers over the Bukkit attribute API - read/set base values for max health, movement speed,
 * attack damage, knockback resistance, etc. (Raw `AttributeModifier` construction is also API; these cover
 * the common "just set the base" case.)
 *
 * ```kotlin
 * zombie.setAttributeBase(Attribute.MOVEMENT_SPEED, 0.35)
 * ```
 */

/** Set the base value of [attribute] (no-op if the entity lacks it). */
public fun Attributable.setAttributeBase(attribute: Attribute, value: Double) {
    getAttribute(attribute)?.baseValue = value
}

/** The current (modifier-applied) value of [attribute], or `null` if absent. */
public fun Attributable.attributeValue(attribute: Attribute): Double? = getAttribute(attribute)?.value

/** The base value of [attribute], or `null` if absent. */
public fun Attributable.attributeBase(attribute: Attribute): Double? = getAttribute(attribute)?.baseValue
