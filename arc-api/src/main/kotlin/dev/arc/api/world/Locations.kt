@file:JvmName("Locations")

package dev.arc.api.world

import org.bukkit.Location
import org.bukkit.Sound

/**
 * Location/vector arithmetic and "do X at this spot" shortcuts.
 *
 * Bukkit's [Location] mutates in place (`add`/`subtract` return `this`), which is a common source of bugs -
 * accidentally moving a shared reference. The operators here always work on a [clone], so locations behave
 * like the immutable values plugin code expects.
 */

/** Non-mutating addition (operates on a clone). */
public operator fun Location.plus(other: Location): Location = clone().add(other)

/** Non-mutating subtraction (operates on a clone). */
public operator fun Location.minus(other: Location): Location = clone().subtract(other)

/** Non-mutating uniform offset (operates on a clone). */
public fun Location.offset(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Location =
    clone().add(x, y, z)

/** The block-centered location for the block containing this position (preserves yaw/pitch). */
public val Location.blockCenter: Location
    get() = Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5, yaw, pitch)

/** Play a sound at this location (no-op if the world is unloaded). */
public fun Location.playSound(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
    world?.playSound(this, sound, volume, pitch)
}

/** 2D (horizontal) distance, ignoring the Y axis. */
public fun Location.horizontalDistanceTo(other: Location): Double {
    val dx = x - other.x
    val dz = z - other.z
    return kotlin.math.sqrt(dx * dx + dz * dz)
}
