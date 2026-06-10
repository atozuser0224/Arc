@file:JvmName("ItemCooldowns")

package dev.arc.api.player

import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Client-side item-use cooldowns (the radial sweep over a hotbar item) via Bukkit API.
 */

/** Put [material] on cooldown for [ticks] ticks for this player. */
public fun Player.setItemCooldown(material: Material, ticks: Int) {
    setCooldown(material, ticks)
}

/** Remaining cooldown ticks for [material] (0 if none). */
public fun Player.itemCooldown(material: Material): Int = getCooldown(material)

/** Whether [material] is currently on cooldown. */
public fun Player.hasItemCooldown(material: Material): Boolean = hasCooldown(material)
