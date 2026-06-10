@file:JvmName("Combat")

package dev.arc.api.combat

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * Combat helpers over the Bukkit damage/velocity API. (Building raw `DamageSource`s and intercepting the
 * vanilla damage pipeline is NMS territory; for cause-attributed damage the API's `damage(amount, source)` is
 * the safe, stable path and is what these wrap.)
 */

/** Deal [amount] damage, optionally attributed to [source] (so kills credit the right killer). */
public fun LivingEntity.hurt(amount: Double, source: Entity? = null) {
    if (source != null) damage(amount, source) else damage(amount)
}

/** Instantly kill this entity (sets health to 0, triggering the normal death pipeline). */
public fun LivingEntity.killInstantly() {
    health = 0.0
}

/** Apply knockback in [direction] (normalised) at [strength], added to current velocity. */
public fun Entity.knockback(direction: Vector, strength: Double) {
    velocity = velocity.add(direction.clone().normalize().multiply(strength))
}

/** Launch this entity along its facing direction at [power]. */
public fun Entity.launchForward(power: Double) {
    velocity = location.direction.normalize().multiply(power)
}

/**
 * Attack-cooldown charge `0.0..1.0` (1.0 = fully charged for max-damage hit). Mirrors the client's cooldown
 * indicator; useful for combat plugins that gate abilities on a full swing.
 */
public val Player.attackCharge: Float
    get() = getAttackCooldown()
