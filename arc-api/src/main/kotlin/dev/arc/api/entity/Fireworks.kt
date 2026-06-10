@file:JvmName("Fireworks")

package dev.arc.api.entity

import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.entity.Firework

/**
 * Launch a firework with a built [FireworkEffect], over Bukkit API.
 *
 * ```kotlin
 * loc.launchFirework(power = 2) {
 *     with(FireworkEffect.Type.BALL_LARGE)
 *     withColor(Color.RED, Color.YELLOW)
 *     withTrail()
 * }
 * ```
 */
public fun Location.launchFirework(power: Int = 1, build: FireworkEffect.Builder.() -> Unit): Firework? {
    val world = world ?: return null
    val firework = world.spawn(this, Firework::class.java)
    val meta = firework.fireworkMeta
    meta.power = power
    meta.addEffect(FireworkEffect.builder().apply(build).build())
    firework.fireworkMeta = meta
    return firework
}
