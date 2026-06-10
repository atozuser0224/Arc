@file:JvmName("Particles")

package dev.arc.api.particle

import org.bukkit.Location
import org.bukkit.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Particle spawning at a location, plus a couple of ready-made shapes. Saves the per-call `world.spawnParticle`
 * boilerplate and the trig for rings/lines that effect code rewrites constantly.
 *
 * ```kotlin
 * player.location.particleCircle(Particle.FLAME, radius = 1.5)
 * from.particleLine(Particle.CRIT, to)
 * ```
 */

/** Spawn [count] of [particle] at this location (no-op if the world is unloaded). */
public fun Location.spawnParticle(
    particle: Particle,
    count: Int = 1,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    offsetZ: Double = 0.0,
    extra: Double = 0.0,
) {
    world?.spawnParticle(particle, this, count, offsetX, offsetY, offsetZ, extra)
}

/** Draw a horizontal ring of [particle] centered on this location. */
public fun Location.particleCircle(particle: Particle, radius: Double, points: Int = 36) {
    val world = world ?: return
    for (i in 0 until points) {
        val angle = 2.0 * PI * i / points
        world.spawnParticle(particle, x + radius * cos(angle), y, z + radius * sin(angle), 1, 0.0, 0.0, 0.0, 0.0)
    }
}

/** Draw a straight line of [particle] from this location to [to], one particle every [spacing] blocks. */
public fun Location.particleLine(particle: Particle, to: Location, spacing: Double = 0.25) {
    val world = world ?: return
    if (to.world != world) return
    val direction = to.toVector().subtract(toVector())
    val length = direction.length()
    if (length == 0.0) return
    val step = direction.normalize().multiply(spacing)
    val point = toVector()
    var travelled = 0.0
    while (travelled <= length) {
        world.spawnParticle(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        point.add(step)
        travelled += spacing
    }
}
