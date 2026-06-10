@file:JvmName("Equipment")

package dev.arc.api.entity

import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack

/**
 * One-call equipment dressing for any [LivingEntity] (player, mob, armor stand) - set only the slots you pass.
 *
 * ```kotlin
 * zombie.equip(helmet = goldHelmet, mainHand = sword)
 * ```
 */
public fun LivingEntity.equip(
    helmet: ItemStack? = null,
    chestplate: ItemStack? = null,
    leggings: ItemStack? = null,
    boots: ItemStack? = null,
    mainHand: ItemStack? = null,
    offHand: ItemStack? = null,
) {
    val gear = equipment ?: return
    helmet?.let { gear.helmet = it }
    chestplate?.let { gear.chestplate = it }
    leggings?.let { gear.leggings = it }
    boots?.let { gear.boots = it }
    mainHand?.let { gear.setItemInMainHand(it) }
    offHand?.let { gear.setItemInOffHand(it) }
}
