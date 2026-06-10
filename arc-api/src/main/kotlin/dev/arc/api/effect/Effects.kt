@file:JvmName("Effects")

package dev.arc.api.effect

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * Per-player sounds and particles via Bukkit's player-scoped methods - the API-first way to give one player
 * sounds/particles others don't see or hear (client-only effects), no packets required.
 */

/** Show [particle] (×[count]) at [location] to this player only. */
public fun Player.particleTo(
    particle: Particle,
    location: Location,
    count: Int = 1,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    offsetZ: Double = 0.0,
    extra: Double = 0.0,
) {
    spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)
}

/** Play [sound] at this player's location, to them only. */
public fun Player.soundSelf(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
    playSound(location, sound, volume, pitch)
}

/** Play [sound] at [location], to this player only. */
public fun Player.soundAt(location: Location, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
    playSound(location, sound, volume, pitch)
}
