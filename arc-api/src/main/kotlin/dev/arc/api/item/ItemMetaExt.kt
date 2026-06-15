@file:JvmName("ItemMetaExtensions")

package dev.arc.api.item

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Typed [ItemMeta] accessors — eliminates the get/cast/set boilerplate.
 *
 * ```kotlin
 * // Before
 * val meta = stack.itemMeta as? SkullMeta ?: return
 * meta.ownerProfile = profile
 * stack.itemMeta = meta
 *
 * // After
 * stack.editMeta<SkullMeta> { ownerProfile = profile }
 *
 * // Read-only access
 * val owner = stack.meta<SkullMeta>()?.ownerProfile
 * ```
 */

/** Returns the item meta cast to [M], or `null` if the meta is absent or of a different type. */
public inline fun <reified M : ItemMeta> ItemStack.meta(): M? = itemMeta as? M

/**
 * Casts the item meta to [M], applies [block], and saves it back.
 * Returns `true` if the meta matched and the edit was applied; `false` otherwise.
 */
public inline fun <reified M : ItemMeta> ItemStack.editMeta(block: M.() -> Unit): Boolean {
    val meta = itemMeta as? M ?: return false
    meta.block()
    itemMeta = meta
    return true
}

/**
 * Like [editMeta] but throws [IllegalStateException] when the meta type doesn't match.
 * Use when you're certain of the item type and want a loud failure instead of a silent no-op.
 */
public inline fun <reified M : ItemMeta> ItemStack.requireMeta(block: M.() -> Unit) {
    val meta = itemMeta as? M
        ?: error("Expected ${M::class.simpleName} but got ${itemMeta?.javaClass?.simpleName} for $type")
    meta.block()
    itemMeta = meta
}
