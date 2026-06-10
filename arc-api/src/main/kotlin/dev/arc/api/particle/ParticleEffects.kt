@file:JvmName("ParticleEffects")

package dev.arc.api.particle

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Spawn particles in a horizontal circle of [radius] at [center]. */
public fun World.particleCircle(
    center: Location,
    particle: Particle,
    radius: Double = 1.0,
    points: Int = 32,
    yOffset: Double = 0.0,
) {
    val step = 2 * PI / points
    for (i in 0 until points) {
        val angle = i * step
        spawnParticle(
            particle,
            center.x + cos(angle) * radius,
            center.y + yOffset,
            center.z + sin(angle) * radius,
            1, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

/** Spawn particles in a sphere of [radius] at [center]. */
public fun World.particleSphere(
    center: Location,
    particle: Particle,
    radius: Double = 1.0,
    points: Int = 64,
) {
    val goldenAngle = PI * (3 - Math.sqrt(5.0))
    for (i in 0 until points) {
        val y = 1 - (i / (points - 1.0)) * 2
        val r = Math.sqrt(1 - y * y)
        val theta = goldenAngle * i
        spawnParticle(
            particle,
            center.x + cos(theta) * r * radius,
            center.y + y * radius,
            center.z + sin(theta) * r * radius,
            1, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

/** Spawn particles in a rising helix at [base]. */
public fun World.particleHelix(
    base: Location,
    particle: Particle,
    height: Double = 2.0,
    radius: Double = 0.5,
    rotations: Int = 3,
    points: Int = 60,
) {
    val totalAngle = rotations * 2 * PI
    for (i in 0 until points) {
        val t = i.toDouble() / points
        val angle = t * totalAngle
        spawnParticle(
            particle,
            base.x + cos(angle) * radius,
            base.y + t * height,
            base.z + sin(angle) * radius,
            1, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

/** Spawn particles in a straight line from [from] to [to]. */
public fun World.particleLine(
    from: Location,
    to: Location,
    particle: Particle,
    density: Double = 0.3,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
    val steps = (dist / density).toInt().coerceAtLeast(1)
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        spawnParticle(particle, from.x + dx * t, from.y + dy * t, from.z + dz * t, 1, 0.0, 0.0, 0.0, 0.0)
    }
}

/** Outline a rectangle on the XZ plane. */
public fun World.particleRect(
    corner1: Location,
    corner2: Location,
    particle: Particle,
    density: Double = 0.5,
) {
    val y = corner1.y
    val corners = listOf(
        Location(this, corner1.x, y, corner1.z),
        Location(this, corner2.x, y, corner1.z),
        Location(this, corner2.x, y, corner2.z),
        Location(this, corner1.x, y, corner2.z),
    )
    for (i in corners.indices) {
        particleLine(corners[i], corners[(i + 1) % corners.size], particle, density)
    }
}
