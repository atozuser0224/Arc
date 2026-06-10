package dev.arc.api.item

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Fluent [ItemStack] builders.
 *
 * ```
 * val sword = item(Material.DIAMOND_SWORD) {
 *     name(Component.text("Excalibur"))
 *     lore(Component.text("Legendary"))
 * }
 * ```
 */
inline fun item(material: Material, amount: Int = 1, block: ItemStack.() -> Unit = {}): ItemStack =
    ItemStack(material, amount).apply(block)

/** Edit this item's [ItemMeta] in place and return the same stack. */
inline fun ItemStack.meta(crossinline block: ItemMeta.() -> Unit): ItemStack {
    editMeta { it.block() }
    return this
}

/** Set the display name (Adventure component). */
fun ItemStack.name(component: Component): ItemStack = meta { displayName(component) }

/** Replace the lore with [lines]. */
fun ItemStack.lore(vararg lines: Component): ItemStack = meta { lore(lines.toList()) }
