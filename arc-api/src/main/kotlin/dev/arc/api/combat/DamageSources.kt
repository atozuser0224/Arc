@file:JvmName("DamageSources")

package dev.arc.api.combat

import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity

/**
 * Typed damage via Paper's [DamageSource] API - the supported way to deal damage of a specific
 * [DamageType] (fire, magic, fall, ...) with an optional attacker, so death messages and resistances behave
 * correctly. (Building a raw NMS `DamageSource` is reachable via the bridge, but rarely needed now.)
 *
 * ```kotlin
 * victim.hurtWith(6.0, DamageType.MAGIC)
 * victim.hurtWith(4.0, DamageType.PLAYER_ATTACK, attacker = player)
 * ```
 */
public fun LivingEntity.hurtWith(amount: Double, type: DamageType, attacker: Entity? = null) {
    val builder = DamageSource.builder(type)
    if (attacker != null) builder.withCausingEntity(attacker).withDirectEntity(attacker)
    damage(amount, builder.build())
}
