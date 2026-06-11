package dev.arc.api.fake

import dev.arc.api.Arc
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * A fake server-side mob entity. Behaves exactly like a real mob — full AI,
 * pathfinding, targeting, and combat events fire normally through NMS.
 *
 * Obtain via [FakeServer.spawnMob]. Always call [remove] when done.
 *
 * ```kotlin
 * val zombie = FakeServer.spawnMob<Zombie>(world, location) {
 *     equip(ItemStack(Material.IRON_SWORD))
 *     setCustomName("Boss")
 *     setNameVisible(true)
 * }
 * zombie.setTarget(realPlayer)
 * zombie.remove()
 * ```
 */
interface FakeMob : FakeEntity {

    override val entity: Mob

    // ── AI ────────────────────────────────────────────────────────────────────

    /** Enable or disable the mob's AI entirely. Disabled mobs stand frozen but still receive damage. */
    fun setAI(enabled: Boolean)

    /** Current attack target. Changing this fires the mob's targeting logic next tick. */
    var target: LivingEntity?
        get() = entity.target
        set(value) { entity.target = value }

    /** Navigate toward [location] using the mob's built-in pathfinding. */
    fun pathfindTo(location: Location, speed: Double = 1.0)

    /** Navigate toward [entity] using the mob's built-in pathfinding. */
    fun pathfindTo(entity: LivingEntity, speed: Double = 1.0)

    // ── Equipment ─────────────────────────────────────────────────────────────

    fun equip(item: ItemStack, slot: EquipmentSlot = EquipmentSlot.HAND)
    fun setDropChance(slot: EquipmentSlot, chance: Float)

    // ── Display ───────────────────────────────────────────────────────────────

    fun setCustomName(name: String?)
    fun setNameVisible(visible: Boolean)
    fun setSilent(silent: Boolean)
    fun setInvisible(invisible: Boolean)
    fun setGlowing(glowing: Boolean)

    // ── Override reset ────────────────────────────────────────────────────────

    override fun reset() {
        entity.health = entity.maxHealth
        entity.activePotionEffects.forEach { entity.removePotionEffect(it.type) }
        entity.target = null
        entity.setAI(true)
    }
}

// ── Internal implementation ───────────────────────────────────────────────────

internal class FakeMobImpl(override val entity: Mob) : FakeMob {

    override fun attack(target: LivingEntity): AttackResult {
        val hpBefore = if (target.isValid) target.health else 0.0
        val landed = Arc.nms.fake.performAttack(entity, target, null)
        val hpAfter = if (target.isValid) target.health else 0.0
        return AttackResult(
            landed = landed,
            damageDealt = (hpBefore - hpAfter).coerceAtLeast(0.0),
            killedTarget = !target.isValid || target.isDead,
        )
    }

    override fun takeDamage(amount: Double, attacker: LivingEntity?, cause: DamageCause): DamageResult {
        val hpBefore = entity.health
        val applied = Arc.nms.fake.applyDamage(entity, amount, attacker, cause)
        val hpAfter = if (entity.isValid) entity.health else 0.0
        return DamageResult(
            applied = applied.takeIf { it > 0.0 } ?: (hpBefore - hpAfter).coerceAtLeast(0.0),
            killed = !entity.isValid || entity.isDead,
        )
    }

    override fun shoot(target: LivingEntity, entityClass: String): ProjectileResult {
        val projectile = Arc.nms.fake.launchProjectile(entity, target, entityClass)
        return ProjectileResult(projectile, projectile != null)
    }

    override fun moveTo(location: Location) {
        Arc.nms.fake.simulateMobMove(entity, location)
    }

    override fun pathfindTo(location: Location, speed: Double) {
        Arc.nms.fake.pathfindMobTo(entity, location, speed)
    }

    override fun pathfindTo(entity: LivingEntity, speed: Double) {
        Arc.nms.fake.pathfindMobToEntity(this.entity, entity, speed)
    }

    override fun lookAt(location: Location) {
        val dir = location.toVector().subtract(entity.location.toVector()).normalize()
        val loc = entity.location.clone().apply { direction = dir }
        entity.teleport(loc)
    }

    override fun setAI(enabled: Boolean) {
        entity.setAI(enabled)
    }

    override fun equip(item: ItemStack, slot: EquipmentSlot) {
        val eq = entity.equipment ?: return
        when (slot) {
            EquipmentSlot.HAND     -> eq.setItemInMainHand(item)
            EquipmentSlot.OFF_HAND -> eq.setItemInOffHand(item)
            EquipmentSlot.HEAD     -> eq.helmet = item
            EquipmentSlot.CHEST    -> eq.chestplate = item
            EquipmentSlot.LEGS     -> eq.leggings = item
            EquipmentSlot.FEET     -> eq.boots = item
            else                   -> eq.setItemInMainHand(item)
        }
    }

    override fun setDropChance(slot: EquipmentSlot, chance: Float) {
        val eq = entity.equipment ?: return
        when (slot) {
            EquipmentSlot.HAND     -> eq.itemInMainHandDropChance = chance
            EquipmentSlot.OFF_HAND -> eq.itemInOffHandDropChance = chance
            EquipmentSlot.HEAD     -> eq.helmetDropChance = chance
            EquipmentSlot.CHEST    -> eq.chestplateDropChance = chance
            EquipmentSlot.LEGS     -> eq.leggingsDropChance = chance
            EquipmentSlot.FEET     -> eq.bootsDropChance = chance
            else -> {}
        }
    }

    override fun setCustomName(name: String?) {
        entity.customName(name?.let { Component.text(it) })
    }

    override fun setNameVisible(visible: Boolean) { entity.isCustomNameVisible = visible }
    override fun setSilent(silent: Boolean)        { entity.isSilent = silent }
    override fun setInvisible(invisible: Boolean)  { entity.isInvisible = invisible }
    override fun setGlowing(glowing: Boolean)      { entity.isGlowing = glowing }

    override fun remove() = Arc.nms.fake.removeFakeMob(entity)
}
