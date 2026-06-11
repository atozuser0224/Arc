package dev.arc.api.fake

import dev.arc.api.Arc
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

// ── Result types ──────────────────────────────────────────────────────────────

data class AttackResult(
    val landed: Boolean,
    val damageDealt: Double,
    val killedTarget: Boolean,
)

data class DamageResult(val applied: Double, val killed: Boolean)

data class ProjectileResult(val projectile: Entity?, val launched: Boolean)

data class InteractResult(val fired: Boolean, val cancelled: Boolean)

// ── Interface ─────────────────────────────────────────────────────────────────

/**
 * A fake server-side player backed by an NMS `ServerPlayer` with a no-op Netty
 * connection. All combat and interaction calls go through the same NMS code paths
 * as a real player — every Bukkit event fires with correct ordering, damage
 * calculations, knockback, and death handling.
 *
 * Obtain via [FakeServer.spawn]. Always call [remove] when done.
 *
 * ```kotlin
 * val bot = FakeServer.spawn(world, "DummyBot", spawnLocation)
 * bot.equip(ItemStack(Material.DIAMOND_SWORD))
 * val result = bot.attack(targetPlayer)
 * println("hit for ${result.damageDealt}, killed=${result.killedTarget}")
 * bot.remove()
 * ```
 */
interface FakePlayer : FakeEntity {

    override val entity: Player
    val player: Player get() = entity

    // ── Equipment ─────────────────────────────────────────────────────────────

    fun equip(item: ItemStack, slot: EquipmentSlot = EquipmentSlot.HAND)
    fun clearInventory()

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Melee attack [target] with an optional [weapon] override (null = current main-hand).
     * Goes through the full NMS `Player.attack()` pipeline: cooldown, crits, sweep AOE.
     */
    fun attack(target: LivingEntity, weapon: ItemStack? = null): AttackResult
    override fun attack(target: LivingEntity): AttackResult = attack(target, null)

    // ── Interaction ───────────────────────────────────────────────────────────

    fun interact(block: Block, face: BlockFace = BlockFace.UP, hand: EquipmentSlot = EquipmentSlot.HAND): InteractResult
    fun interact(entity: Entity, hand: EquipmentSlot = EquipmentSlot.HAND): InteractResult

    // ── State ─────────────────────────────────────────────────────────────────

    override fun reset() {
        player.health = player.maxHealth
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        clearInventory()
    }
}

// ── Internal implementation ───────────────────────────────────────────────────

internal class FakePlayerImpl(override val entity: Player) : FakePlayer {

    override fun equip(item: ItemStack, slot: EquipmentSlot) {
        when (slot) {
            EquipmentSlot.HAND     -> entity.inventory.setItemInMainHand(item)
            EquipmentSlot.OFF_HAND -> entity.inventory.setItemInOffHand(item)
            EquipmentSlot.HEAD     -> entity.inventory.helmet = item
            EquipmentSlot.CHEST    -> entity.inventory.chestplate = item
            EquipmentSlot.LEGS     -> entity.inventory.leggings = item
            EquipmentSlot.FEET     -> entity.inventory.boots = item
            else                   -> entity.inventory.setItemInMainHand(item)
        }
    }

    override fun clearInventory() = entity.inventory.clear()

    override fun attack(target: LivingEntity, weapon: ItemStack?): AttackResult {
        if (weapon != null) equip(weapon)
        val hpBefore = if (target.isValid) target.health else 0.0
        val landed = Arc.nms.fake.performAttack(entity, target, weapon)
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

    override fun interact(block: Block, face: BlockFace, hand: EquipmentSlot): InteractResult {
        Arc.nms.fake.performBlockInteract(entity, block, face, hand)
        return InteractResult(fired = true, cancelled = false)
    }

    override fun interact(target: Entity, hand: EquipmentSlot): InteractResult {
        Arc.nms.fake.performEntityInteract(entity, target, hand)
        return InteractResult(fired = true, cancelled = false)
    }

    override fun moveTo(location: Location) {
        Arc.nms.fake.simulateMove(entity, location)
    }

    override fun lookAt(location: Location) {
        val dir = location.toVector().subtract(entity.location.toVector()).normalize()
        val loc = entity.location.clone().apply { direction = dir }
        entity.teleport(loc)
    }

    override fun remove() = Arc.nms.fake.removeFakePlayer(entity)
}
