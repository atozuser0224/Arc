@file:JvmName("RayTrace")

package dev.arc.api.world

import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.util.RayTraceResult

/**
 * Ray casting over Bukkit's ray-trace API - "what block/entity am I looking at" without reimplementing
 * DDA traversal.
 *
 * ```kotlin
 * val hit = player.eyeLocation.rayTraceBlocks(maxDistance = 50.0)?.hitBlock
 * ```
 */

/** Ray-trace blocks from this location along its facing direction. */
public fun Location.rayTraceBlocks(
    maxDistance: Double,
    fluids: FluidCollisionMode = FluidCollisionMode.NEVER,
): RayTraceResult? = world?.rayTraceBlocks(this, direction, maxDistance, fluids, true)

/** Ray-trace entities from this location along its facing direction. */
public fun Location.rayTraceEntities(maxDistance: Double): RayTraceResult? =
    world?.rayTraceEntities(this, direction, maxDistance)
