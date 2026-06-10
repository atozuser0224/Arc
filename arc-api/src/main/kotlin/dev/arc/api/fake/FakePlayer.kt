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

/**
 * Outcome of a simulated melee attack.
 *
 * @property landed      attack was dispatched through NMS (not the same as damage
 *   being dealt — the event may have been cancelled, resulting in [damageDealt] == 0)
 * @property damageDealt health difference on [target] after the attack (0 if cancelled)
 * @property killedTarget target died as a result of this attack
 */
data class AttackResult(
    val landed: Boolean,
    val damageDealt: Double,
    val killedTarget: Boolean,
)

/**
 * Outcome of a simulated damage application.
 *
 * @property applied actual damage applied after armor / effects (0 if cancelled)
 * @property killed  entity died from this damage
 */
data class DamageResult(val applied: Double, val killed: Boolean)

/**
 * Outcome of a simulated projectile launch.
 *
 * @property projectile the spawned entity, or null if the type could not be resolved
 * @property launched   false only if projectile creation failed entirely
 */
data class ProjectileResult(val projectile: Entity?, val launched: Boolean)

/** Outcome of a block or entity interaction simulation. */
data class InteractResult(val fired: Boolean, val cancelled: Boolean)

// ── Interface ─────────────────────────────────────────────────────────────────

/**
 * A server-side fake player backed by an NMS `ServerPlayer` with a no-op Netty
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
interface FakePlayer {

    /** The underlying Bukkit [Player]. Don't bypass simulation methods to modify it directly. */
    val player: Player

    val name: String get() = player.name
    val health: Double get() = player.health
    val isDead: Boolean get() = !player.isValid || player.isDead
    val location: Location get() = player.location

    // ── Equipment ─────────────────────────────────────────────────────────────

    fun equip(item: ItemStack, slot: EquipmentSlot = EquipmentSlot.HAND)
    fun clearInventory()

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Simulate a full melee attack on [target].
     * Uses [weapon] as the main-hand item for this hit (or current equipment if null).
     * Goes through the full NMS `Player.attack()` pipeline.
     */
    fun attack(target: LivingEntity, weapon: ItemStack? = null): AttackResult

    /**
     * Receive [amount] damage from [attacker] (or environmental if null).
     * Fires through the NMS `LivingEntity.hurt()` pipeline.
     */
    fun takeDamage(
        amount: Double,
        attacker: LivingEntity? = null,
        cause: DamageCause = DamageCause.ENTITY_ATTACK,
    ): DamageResult

    /**
     * Launch a [entityClass] projectile (e.g. `"Arrow"`, `"Snowball"`) aimed at [target].
     */
    fun shoot(target: LivingEntity, entityClass: String = "Arrow"): ProjectileResult

    // ── Interaction ───────────────────────────────────────────────────────────

    fun interact(block: Block, face: BlockFace = BlockFace.UP, hand: EquipmentSlot = EquipmentSlot.HAND): InteractResult
    fun interact(entity: Entity, hand: EquipmentSlot = EquipmentSlot.HAND): InteractResult

    // ── Movement ──────────────────────────────────────────────────────────────

    /** Move to [location], firing [org.bukkit.event.player.PlayerMoveEvent]. */
    fun moveTo(location: Location)

    fun lookAt(location: Location)
    fun lookAt(entity: Entity) = lookAt(entity.location)

    // ── State ─────────────────────────────────────────────────────────────────

    fun setHealth(amount: Double) {
        player.health = amount.coerceIn(0.0, player.maxHealth)
    }

    /** Restore full health, clear potion effects and inventory. */
    fun reset() {
        player.health = player.maxHealth
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        clearInventory()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun remove()
}

// ── Internal implementation ───────────────────────────────────────────────────

internal class FakePlayerImpl(override val player: Player) : FakePlayer {

    override fun equip(item: ItemStack, slot: EquipmentSlot) {
        when (slot) {
            EquipmentSlot.HAND      -> player.inventory.setItemInMainHand(item)
            EquipmentSlot.OFF_HAND  -> player.inventory.setItemInOffHand(item)
            EquipmentSlot.HEAD      -> player.inventory.helmet = item
            EquipmentSlot.CHEST     -> player.inventory.chestplate = item
            EquipmentSlot.LEGS      -> player.inventory.leggings = item
            EquipmentSlot.FEET      -> player.inventory.boots = item
            else                    -> player.inventory.setItemInMainHand(item)
        }
    }

    override fun clearInventory() = player.inventory.clear()

    override fun attack(target: LivingEntity, weapon: ItemStack?): AttackResult {
        if (weapon != null) equip(weapon)
        val hpBefore = if (target.isValid) target.health else 0.0
        val landed = Arc.nms.fake.performAttack(player, target, weapon)
        val hpAfter = if (target.isValid) target.health else 0.0
        return AttackResult(
            landed = landed,
            damageDealt = (hpBefore - hpAfter).coerceAtLeast(0.0),
            killedTarget = !target.isValid || target.isDead,
        )
    }

    override fun takeDamage(amount: Double, attacker: LivingEntity?, cause: DamageCause): DamageResult {
        val hpBefore = player.health
        val applied = Arc.nms.fake.applyDamage(player, amount, attacker, cause)
        val hpAfter = if (player.isValid) player.health else 0.0
        return DamageResult(
            applied = applied.takeIf { it > 0 } ?: (hpBefore - hpAfter).coerceAtLeast(0.0),
            killed = !player.isValid || player.isDead,
        )
    }

    override fun shoot(target: LivingEntity, entityClass: String): ProjectileResult {
        val entity = Arc.nms.fake.launchProjectile(player, target, entityClass)
        return ProjectileResult(entity, entity != null)
    }

    override fun interact(block: Block, face: BlockFace, hand: EquipmentSlot): InteractResult {
        Arc.nms.fake.performBlockInteract(player, block, face, hand)
        return InteractResult(fired = true, cancelled = false)
    }

    override fun interact(entity: Entity, hand: EquipmentSlot): InteractResult {
        Arc.nms.fake.performEntityInteract(player, entity, hand)
        return InteractResult(fired = true, cancelled = false)
    }

    override fun moveTo(location: Location) {
        Arc.nms.fake.simulateMove(player, location)
    }

    override fun lookAt(location: Location) {
        val dir = location.toVector().subtract(player.location.toVector()).normalize()
        val loc = player.location.clone().apply { direction = dir }
        player.teleport(loc)
    }

    override fun remove() = Arc.nms.fake.removeFakePlayer(player)
}
