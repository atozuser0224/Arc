@file:JvmName("Explosions")

package dev.arc.api.world

import org.bukkit.Location

/**
 * Explosion helpers over Bukkit's `createExplosion`. (Hooking the internal explosion algorithm - blast
 * resistance overrides, custom ray counts - is NMS; for "make a boom here" the API is enough.)
 */

/** Create an explosion of [power] here, optionally setting [fire] and/or [breakBlocks]. */
public fun Location.explode(power: Float, fire: Boolean = false, breakBlocks: Boolean = true) {
    world?.createExplosion(this, power, fire, breakBlocks)
}

/** A purely cosmetic explosion (no block damage, no fire). */
public fun Location.explodeCosmetic(power: Float) {
    world?.createExplosion(this, power, false, false)
}
