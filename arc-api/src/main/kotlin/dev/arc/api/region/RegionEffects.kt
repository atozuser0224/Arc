@file:JvmName("RegionEffects")

package dev.arc.api.region

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/** All online players currently inside this [Cuboid]. */
public fun Cuboid.playersInside(): List<Player> =
    world.players.filter { contains(it.location) }

/** Apply a [PotionEffect] to every player inside this region. */
public fun Cuboid.applyEffect(type: PotionEffectType, duration: Int = 60, amplifier: Int = 0) {
    playersInside().forEach {
        it.addPotionEffect(PotionEffect(type, duration, amplifier, true, false))
    }
}

/** Remove a [PotionEffectType] from every player inside this region. */
public fun Cuboid.removeEffect(type: PotionEffectType) {
    playersInside().forEach { it.removePotionEffect(type) }
}

/** Play a [sound] to all players inside this region. */
public fun Cuboid.playSound(sound: Sound, volume: Float = 1f, pitch: Float = 1f) {
    val loc = center()
    playersInside().forEach { it.playSound(loc, sound, volume, pitch) }
}

/** Fill this region with particle outlines each tick (call from a timer). */
public fun Cuboid.outlineParticles(particle: Particle, density: Double = 0.5) {
    val w = world
    // edges of the bounding box
    val corners = listOf(
        min, org.bukkit.Location(w, max.x, min.y, min.z),
        org.bukkit.Location(w, min.x, min.y, max.z), org.bukkit.Location(w, max.x, min.y, max.z),
        org.bukkit.Location(w, min.x, max.y, min.z), org.bukkit.Location(w, max.x, max.y, min.z),
        org.bukkit.Location(w, min.x, max.y, max.z), max,
    )
    // 12 edges
    val edges = listOf(
        0 to 1, 0 to 2, 1 to 3, 2 to 3,
        4 to 5, 4 to 6, 5 to 7, 6 to 7,
        0 to 4, 1 to 5, 2 to 6, 3 to 7,
    )
    for ((a, b) in edges) {
        val from = corners[a]; val to = corners[b]
        val dx = to.x - from.x; val dy = to.y - from.y; val dz = to.z - from.z
        val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        val steps = (dist / density).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            w.spawnParticle(particle, from.x + dx * t, from.y + dy * t, from.z + dz * t, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
