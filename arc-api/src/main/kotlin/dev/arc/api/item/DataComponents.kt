@file:JvmName("DataComponents")

package dev.arc.api.item

import io.papermc.paper.datacomponent.DataComponentType
import org.bukkit.inventory.ItemStack

/**
 * Item Data Component access via Paper's API - the 1.20.5+ replacement for raw NBT editing. Set/get/remove
 * components (food, tool, custom model data, attribute modifiers, ...) in a type-safe way; pass the constants
 * from `io.papermc.paper.datacomponent.DataComponentTypes`.
 *
 * ```kotlin
 * stack.setComponent(DataComponentTypes.MAX_STACK_SIZE, 16)
 * val size = stack.getComponent(DataComponentTypes.MAX_STACK_SIZE)
 * ```
 */

/** Set a valued data component, returning the same stack for chaining. */
public fun <T : Any> ItemStack.setComponent(type: DataComponentType.Valued<T>, value: T): ItemStack = apply {
    setData(type, value)
}

/** Read a valued data component, or `null` if unset. */
public fun <T : Any> ItemStack.getComponent(type: DataComponentType.Valued<T>): T? = getData(type)

/** Whether this stack has [type] set. */
public fun ItemStack.hasComponent(type: DataComponentType): Boolean = hasData(type)

/** Remove a data component, returning the same stack for chaining. */
public fun ItemStack.removeComponent(type: DataComponentType): ItemStack = apply {
    unsetData(type)
}
