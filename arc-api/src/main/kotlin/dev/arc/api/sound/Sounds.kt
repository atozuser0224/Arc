@file:JvmName("Sounds")

package dev.arc.api.sound

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.World
import org.bukkit.entity.Player

/** Play [sound] to this player at their location. */
public fun Player.playSound(
    sound: Sound,
    volume: Float = 1f,
    pitch: Float = 1f,
    category: SoundCategory = SoundCategory.MASTER,
) {
    playSound(location, sound, category, volume, pitch)
}

/** Play [sound] with a random pitch in [pitchRange]. */
public fun Player.playSoundRandom(
    sound: Sound,
    volume: Float = 1f,
    pitchRange: ClosedFloatingPointRange<Float> = 0.8f..1.2f,
) {
    val pitch = pitchRange.start + Math.random().toFloat() * (pitchRange.endInclusive - pitchRange.start)
    playSound(sound, volume, pitch)
}

/** Broadcast [sound] to all players within [radius] blocks of [location]. */
public fun World.broadcastSound(
    location: Location,
    sound: Sound,
    volume: Float = 1f,
    pitch: Float = 1f,
    radius: Double = 16.0,
) {
    players.filter { it.location.distanceSquared(location) <= radius * radius }
        .forEach { it.playSound(location, sound, volume, pitch) }
}

/** Stop a specific sound for this player. */
public fun Player.stopSound(sound: Sound, category: SoundCategory = SoundCategory.MASTER) {
    stopSound(sound, category)
}

/** Stop ALL sounds for this player. */
public fun Player.stopAllSounds() {
    SoundCategory.entries.forEach { cat -> Sound.entries.forEach { s -> stopSound(s, cat) } }
}
