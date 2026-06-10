@file:JvmName("WorldBorders")

package dev.arc.api.world

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Per-player world borders via Paper API - show one player a border (mini-game arena, claim outline) the rest
 * of the server doesn't have, without touching the world's real border.
 */

/** Show this player a personal world border centered at [center] with the given [radius]. */
public fun Player.showWorldBorder(center: Location, radius: Double) {
    val border = Bukkit.createWorldBorder()
    border.center = center
    border.size = radius * 2.0
    worldBorder = border
}

/** Clear this player's personal world border (revert to the world's). */
public fun Player.clearWorldBorder() {
    worldBorder = null
}
