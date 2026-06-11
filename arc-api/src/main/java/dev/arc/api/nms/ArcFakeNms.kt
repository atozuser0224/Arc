package dev.arc.api.nms

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * NMS operations that simulate a real server's event pipeline using fake entities.
 *
 * Every method calls the same NMS code path a genuine action would take, so all
 * Bukkit events fire with correct ordering, damage calculations (armor, potion
 * effects, enchantments), knockback, and death handling — indistinguishable from
 * a real entity to plugins listening on those events.
 *
 * Access via [dev.arc.api.Arc.nms.fake].
 */
interface ArcFakeNms {

    // ── Player lifecycle ──────────────────────────────────────────────────────

    /**
     * Spawn a fake NMS `ServerPlayer` in [world] at [location] with [name].
     * The backing entity has a no-op Netty channel — packets are silently
     * discarded and no join / login events fire. All other server-side logic
     * (chunk loading, entity tracking, event dispatch) works normally.
     *
     * The returned [Player] is a real `CraftPlayer`, so `damager is Player`
     * returns true for attacks made by this fake.
     */
    fun spawnFakePlayer(world: World, name: String, location: Location): Player

    /** Remove and untrack a fake player. No-op on real players. */
    fun removeFakePlayer(player: Player)

    /** Returns true if [entity] was created by [spawnFakePlayer]. */
    fun isFakePlayer(entity: Entity): Boolean

    // ── Mob lifecycle ─────────────────────────────────────────────────────────

    /**
     * Spawn a fake mob of [entityType] in [world] at [location].
     * The mob is a real server-side entity with full AI, pathfinding, and combat.
     * It is tagged so [isFakeMob] returns true, but is otherwise indistinguishable
     * from a naturally spawned mob.
     */
    fun spawnFakeMob(world: World, entityType: EntityType, location: Location): LivingEntity

    /** Remove a fake mob. No-op on non-fake entities. */
    fun removeFakeMob(entity: LivingEntity)

    /** Returns true if [entity] was created by [spawnFakeMob]. */
    fun isFakeMob(entity: Entity): Boolean

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Simulate [attacker] performing a full melee attack on [target].
     *
     * For Player attackers: follows the NMS `Player.attack(Entity)` path —
     * attack-cooldown, crit, enchantments, armor reduction, knockback, sweep AOE.
     *
     * For mob attackers: calls NMS `Mob.doHurtTarget()` — damage attributes,
     * knockback, and mob-specific effects.
     *
     * Fires [org.bukkit.event.entity.EntityDamageByEntityEvent] (cancellable).
     *
     * @param weapon overrides the attacker's main-hand item (null = use current).
     * @return true if the NMS method was dispatched.
     */
    fun performAttack(attacker: LivingEntity, target: LivingEntity, weapon: ItemStack?): Boolean

    /**
     * Apply [amount] raw damage through the full NMS `hurt()` pipeline with [cause].
     * Fires [org.bukkit.event.entity.EntityDamageEvent] (cancellable).
     *
     * @return damage actually applied after armor and effects; 0.0 if cancelled.
     */
    fun applyDamage(target: LivingEntity, amount: Double, source: Entity?, cause: DamageCause): Double

    /**
     * Launch a [entityClass] projectile (`"Arrow"`, `"Snowball"`, `"Fireball"`, …)
     * from [shooter] aimed at [target]. The projectile is a real tracked entity.
     *
     * @return the spawned projectile, or null if the class was not resolved.
     */
    fun launchProjectile(shooter: LivingEntity, target: LivingEntity, entityClass: String): Entity?

    // ── Player interaction ────────────────────────────────────────────────────

    /**
     * Simulate [actor] right-clicking [block] on [face] with [hand].
     * Fires [org.bukkit.event.player.PlayerInteractEvent]; triggers block use if not cancelled.
     */
    fun performBlockInteract(actor: Player, block: Block, face: BlockFace, hand: EquipmentSlot)

    /**
     * Simulate [actor] right-clicking [target] with [hand].
     * Fires [org.bukkit.event.player.PlayerInteractEntityEvent]; triggers entity interaction if not cancelled.
     */
    fun performEntityInteract(actor: Player, target: Entity, hand: EquipmentSlot)

    // ── Movement ──────────────────────────────────────────────────────────────

    /**
     * Move the fake player to [to], firing [org.bukkit.event.player.PlayerMoveEvent].
     * @return false if cancelled.
     */
    fun simulateMove(player: Player, to: Location): Boolean

    /**
     * Teleport a fake mob to [to], firing [org.bukkit.event.entity.EntityTeleportEvent].
     * @return false if cancelled.
     */
    fun simulateMobMove(mob: Mob, to: Location): Boolean

    /**
     * Start NMS pathfinding navigation toward [location] at [speed] (1.0 = normal walk speed).
     */
    fun pathfindMobTo(mob: Mob, location: Location, speed: Double)

    /**
     * Start NMS pathfinding navigation toward [target] entity at [speed].
     */
    fun pathfindMobToEntity(mob: Mob, target: LivingEntity, speed: Double)
}
