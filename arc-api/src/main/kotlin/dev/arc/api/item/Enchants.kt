@file:JvmName("Enchants")

package dev.arc.api.item

import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

/** Add [enchantment] at [level], ignoring level/applicability restrictions. */
public fun ItemStack.enchant(enchantment: Enchantment, level: Int = 1): ItemStack {
    addUnsafeEnchantment(enchantment, level)
    return this
}

/** Add multiple enchantments at once. */
public fun ItemStack.enchant(vararg pairs: Pair<Enchantment, Int>): ItemStack {
    pairs.forEach { (e, l) -> addUnsafeEnchantment(e, l) }
    return this
}

/** Remove an enchantment. */
public fun ItemStack.unenchant(enchantment: Enchantment): ItemStack {
    removeEnchantment(enchantment)
    return this
}

/** True if this item has the given enchantment. */
public fun ItemStack.hasEnchant(enchantment: Enchantment): Boolean =
    containsEnchantment(enchantment)

/** The level of [enchantment], or 0 if absent. */
public fun ItemStack.enchantLevel(enchantment: Enchantment): Int =
    getEnchantmentLevel(enchantment)

/** Lookup an enchantment by its namespaced key string (e.g. "minecraft:sharpness"). */
public fun enchantment(key: String): Enchantment? =
    Enchantment.getByKey(NamespacedKey.fromString(key))

/** Make this item glow (adds Luck at level 0 with HIDE_ENCHANTS flag for visual-only glow). */
public fun ItemStack.glow(): ItemStack {
    addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1)
    val meta = itemMeta ?: return this
    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
    itemMeta = meta
    return this
}
