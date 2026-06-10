@file:JvmName("Inventories")

package dev.arc.api.inventory

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Quality-of-life helpers for inventories - the "add this, drop the overflow" and "is there room" checks that
 * every plugin reimplements.
 */

/** `true` when there is no empty slot left. */
public val Inventory.isFull: Boolean
    get() = firstEmpty() == -1

/** Index of the first empty slot, or `null` if full. */
public fun Inventory.firstEmptyOrNull(): Int? = firstEmpty().takeIf { it != -1 }

/** Enables `material in inventory`. */
public operator fun Inventory.contains(material: Material): Boolean = this.contains(material)

/**
 * Add [items] to this inventory, dropping whatever does not fit at [dropLocation] so nothing is silently
 * lost. Returns the number of stacks that had to be dropped.
 */
public fun Inventory.addOrDrop(dropLocation: Location, vararg items: ItemStack): Int {
    val leftover = addItem(*items)
    val world = dropLocation.world
    if (world != null) {
        for (item in leftover.values) {
            world.dropItemNaturally(dropLocation, item)
        }
    }
    return leftover.size
}

/**
 * Give [items] to this player, dropping any overflow at their feet. The structured-concurrency-free analogue
 * of the classic `getInventory().addItem(...)` + manual drop loop.
 */
public fun HumanEntity.give(vararg items: ItemStack) {
    val leftover = inventory.addItem(*items)
    for (item in leftover.values) {
        world.dropItemNaturally(location, item)
    }
}
