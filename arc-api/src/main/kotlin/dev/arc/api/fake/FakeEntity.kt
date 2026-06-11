package dev.arc.api.fake

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

/**
 * Common base for all fake server-side entities — [FakePlayer] and [FakeMob].
 *
 * Backed by real NMS objects; every event and damage calculation goes through the
 * same code paths as genuine entities. Plugins and other entities cannot distinguish
 * a fake entity from a real one through normal Bukkit/NMS APIs.
 */
interface FakeEntity {

    /** The backing [LivingEntity]. Use simulation methods rather than modifying this directly. */
    val entity: LivingEntity

    val health: Double get() = entity.health
    val maxHealth: Double get() = entity.maxHealth
    val isDead: Boolean get() = !entity.isValid || entity.isDead
    val isValid: Boolean get() = entity.isValid
    val location: Location get() = entity.location

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Melee attack [target] through the NMS attack pipeline.
     * Fires [org.bukkit.event.entity.EntityDamageByEntityEvent].
     */
    fun attack(target: LivingEntity): AttackResult

    /**
     * Receive [amount] damage via the full NMS `hurt()` pipeline.
     * Fires [org.bukkit.event.entity.EntityDamageEvent] (cancellable).
     */
    fun takeDamage(
        amount: Double,
        attacker: LivingEntity? = null,
        cause: DamageCause = DamageCause.ENTITY_ATTACK,
    ): DamageResult

    /** Launch a [entityClass] projectile (e.g. `"Arrow"`, `"Snowball"`) aimed at [target]. */
    fun shoot(target: LivingEntity, entityClass: String = "Arrow"): ProjectileResult

    // ── Movement ──────────────────────────────────────────────────────────────

    fun moveTo(location: Location)
    fun lookAt(location: Location)
    fun lookAt(target: Entity) = lookAt(target.location)

    // ── State ─────────────────────────────────────────────────────────────────

    fun setHealth(amount: Double) {
        entity.health = amount.coerceIn(0.0, entity.maxHealth)
    }

    fun reset() {
        entity.health = entity.maxHealth
        entity.activePotionEffects.forEach { entity.removePotionEffect(it.type) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun remove()
}
