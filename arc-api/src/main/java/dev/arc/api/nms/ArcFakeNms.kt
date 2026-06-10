package dev.arc.api.nms

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * NMS operations that simulate a real server's event pipeline using fake entities.
 *
 * Every method calls the same NMS code path a genuine player action would take,
 * so all Bukkit events fire with correct ordering, damage calculations (armor,
 * potion effects, enchantments), knockback, and death handling — indistinguishable
 * from a real player to plugins listening on those events.
 *
 * Access via [dev.arc.api.Arc.nms.fake].
 *
 * ```kotlin
 * val bot = Arc.nms.fake.spawnFakePlayer(world, "Bot", spawnLoc)
 * Arc.nms.fake.performAttack(bot, targetPlayer, sword)
 * Arc.nms.fake.removeFakePlayer(bot)
 * ```
 */
interface ArcFakeNms {

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Spawn a fake NMS `ServerPlayer` in [world] at [location] with [name].
     *
     * The backing entity has a no-op Netty channel — packets are silently
     * discarded and no join / login events fire. All other server-side logic
     * (chunk loading, entity tracking, event dispatch) works normally.
     *
     * The returned [Player] is a real Bukkit `CraftPlayer` backed by an NMS
     * `ServerPlayer`, so `EntityDamageByEntityEvent.damager is Player` returns
     * true for attacks made by this fake.
     */
    fun spawnFakePlayer(world: World, name: String, location: Location): Player

    /** Remove and untrack a fake player. No-op on real players. */
    fun removeFakePlayer(player: Player)

    /** Returns true if [entity] was created by [spawnFakePlayer]. */
    fun isFakePlayer(entity: Entity): Boolean

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Simulate [attacker] performing a full melee attack on [target].
     *
     * Follows the NMS `Player.attack(Entity)` path:
     * attack-cooldown check → critical-hit calculation → weapon enchantments
     * → armor / effect reduction → event dispatch → knockback → sweep AOE.
     *
     * Fired events:
     * - [org.bukkit.event.entity.EntityDamageByEntityEvent] (cancellable)
     * - [org.bukkit.event.entity.EntityDeathEvent] /
     *   [org.bukkit.event.player.PlayerDeathEvent] (if lethal)
     *
     * @param weapon overrides the attacker's main-hand item for this hit.
     *   Pass null to use whatever is currently equipped.
     * @return true if the NMS attack method was dispatched (regardless of event
     *   cancellation or actual damage dealt).
     */
    fun performAttack(attacker: LivingEntity, target: LivingEntity, weapon: ItemStack?): Boolean

    /**
     * Apply [amount] raw damage to [target] through the full NMS `hurt()` pipeline
     * attributed to [source] (or environmental if null) with [cause] as the type.
     *
     * Fired events:
     * - [org.bukkit.event.entity.EntityDamageEvent] (cancellable)
     * - [org.bukkit.event.entity.EntityDeathEvent] /
     *   [org.bukkit.event.player.PlayerDeathEvent] (if lethal)
     *
     * @return damage actually applied after armor and effects; 0.0 if cancelled.
     */
    fun applyDamage(target: LivingEntity, amount: Double, source: Entity?, cause: DamageCause): Double

    /**
     * Launch a projectile of type [entityClass] (simple name: `"Arrow"`,
     * `"Snowball"`, `"Fireball"`, …) from [shooter] aimed at [target].
     * The projectile is a real server-side entity tracked over real ticks.
     *
     * @return the spawned projectile, or null if the class was not resolved.
     */
    fun launchProjectile(shooter: LivingEntity, target: LivingEntity, entityClass: String): Entity?

    // ── Interaction ───────────────────────────────────────────────────────────

    /**
     * Simulate [actor] right-clicking [block] on [face] with [hand].
     *
     * Fires [org.bukkit.event.player.PlayerInteractEvent].
     * If not cancelled, the block's NMS use action is also triggered.
     */
    fun performBlockInteract(actor: Player, block: Block, face: BlockFace, hand: EquipmentSlot)

    /**
     * Simulate [actor] right-clicking [target] with [hand].
     *
     * Fires [org.bukkit.event.player.PlayerInteractEntityEvent].
     * If not cancelled, the entity's NMS interaction handler is called.
     */
    fun performEntityInteract(actor: Player, target: Entity, hand: EquipmentSlot)

    // ── Movement ──────────────────────────────────────────────────────────────

    /**
     * Move the fake player's server-side position to [to].
     *
     * Fires [org.bukkit.event.player.PlayerMoveEvent].
     * If cancelled, the player stays at the current position and false is returned.
     */
    fun simulateMove(player: Player, to: Location): Boolean
}
