@file:JvmName("Items")

package dev.arc.api.item

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * A fluent [ItemStack] builder over the stable `ItemMeta` API (display name, lore, enchants, flags, glint,
 * model data, unbreakable). For deep custom data prefer Paper's Data Components directly; this covers the
 * 90% case that every plugin re-writes.
 *
 * ```kotlin
 * val wand = item(Material.BLAZE_ROD) {
 *     name("<light_purple>Wand".mini())
 *     lore("<gray>Right-click to cast".mini())
 *     glowing()
 *     unbreakable()
 * }
 * ```
 */
public class ItemBuilder internal constructor(material: Material, amount: Int) {

    private val item = ItemStack(material, amount)

    private inline fun edit(block: ItemMeta.() -> Unit) {
        val meta = item.itemMeta ?: return
        meta.block()
        item.itemMeta = meta
    }

    /** Set the display name. */
    public fun name(component: Component): ItemBuilder = apply { edit { displayName(component) } }

    /** Set the lore lines. */
    public fun lore(lines: List<Component>): ItemBuilder = apply { edit { lore(lines) } }

    /** Set the lore lines. */
    public fun lore(vararg lines: Component): ItemBuilder = apply { edit { lore(lines.toList()) } }

    /** Set the stack size. */
    public fun amount(value: Int): ItemBuilder = apply { item.amount = value }

    /** Add an enchantment (ignoring level restrictions). */
    public fun enchant(enchantment: Enchantment, level: Int = 1): ItemBuilder =
        apply { edit { addEnchant(enchantment, level, true) } }

    /** Add display flags (e.g. [ItemFlag.HIDE_ATTRIBUTES]). */
    public fun flags(vararg flags: ItemFlag): ItemBuilder = apply { edit { addItemFlags(*flags) } }

    /** Toggle the enchantment glint without any actual enchantment. */
    public fun glowing(value: Boolean = true): ItemBuilder = apply { edit { setEnchantmentGlintOverride(value) } }

    /** Toggle unbreakable. */
    public fun unbreakable(value: Boolean = true): ItemBuilder = apply { edit { isUnbreakable = value } }

    /** Set the custom model data used by resource packs. */
    public fun modelData(data: Int): ItemBuilder = apply { edit { setCustomModelData(data) } }

    /** A defensive copy of the built stack. */
    public fun build(): ItemStack = item.clone()
}

/** Build an [ItemStack] of [material] (default amount 1) via the [build] DSL. */
public fun item(material: Material, amount: Int = 1, build: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material, amount).apply(build).build()
