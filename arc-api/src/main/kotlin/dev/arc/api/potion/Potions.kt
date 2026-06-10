@file:JvmName("Potions")

package dev.arc.api.potion

import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Concise potion-effect application. Wraps the `addPotionEffect(PotionEffect(type, dur, amp, ...))` ceremony
 * and adds bulk clear / presence checks.
 *
 * ```kotlin
 * player.applyEffect(PotionEffectType.SPEED, durationTicks = 200, amplifier = 1)
 * if (player.hasEffect(PotionEffectType.POISON)) player.clearEffects()
 * ```
 */

/** Apply a potion effect; defaults to non-ambient with particles, matching command behaviour. */
public fun LivingEntity.applyEffect(
    type: PotionEffectType,
    durationTicks: Int,
    amplifier: Int = 0,
    ambient: Boolean = false,
    particles: Boolean = true,
    icon: Boolean = true,
) {
    addPotionEffect(PotionEffect(type, durationTicks, amplifier, ambient, particles, icon))
}

/** Remove every active potion effect. */
public fun LivingEntity.clearEffects() {
    for (effect in activePotionEffects) {
        removePotionEffect(effect.type)
    }
}

/** `true` if this entity currently has the given effect. */
public fun LivingEntity.hasEffect(type: PotionEffectType): Boolean = hasPotionEffect(type)
