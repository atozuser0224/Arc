package dev.arc.api.fake

import dev.arc.api.Arc
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob

/**
 * Entry point for spawning and managing fake server-side entities.
 *
 * Both [FakePlayer] and [FakeMob] are backed by real NMS objects. Every combat
 * action goes through the full NMS pipeline — plugins receive the same Bukkit
 * events they would from a real player or mob, with correct `damager` types,
 * armor reduction, knockback, and death handling.
 *
 * Requires `arc:nms.fake` feature flag to be enabled (default: on).
 *
 * ```kotlin
 * // One-off fake player
 * val bot = FakeServer.spawn(world, "DummyBot", location)
 * bot.equip(ItemStack(Material.DIAMOND_SWORD))
 * bot.attack(realPlayer)
 * bot.remove()
 *
 * // One-off fake mob
 * val zombie = FakeServer.spawnMob<Zombie>(world, location) {
 *     equip(ItemStack(Material.IRON_SWORD))
 *     setCustomName("Elite Zombie")
 *     setNameVisible(true)
 * }
 * zombie.setTarget(realPlayer)
 * zombie.remove()
 *
 * // Auto-cleanup session with both types
 * FakeServer.session(world, arenaCenter) {
 *     val attacker = spawnPlayer("Bot_A")
 *     val defender = spawnMob<Zombie>("Bot_B")
 *     attacker.attack(defender.entity)
 * }
 * ```
 */
object FakeServer {

    // ── Players ───────────────────────────────────────────────────────────────

    /**
     * Spawn a fake NMS player in [world] at [location].
     * Caller is responsible for calling [FakePlayer.remove] when done.
     */
    fun spawn(world: World, name: String, location: Location): FakePlayer {
        val player = Arc.nms.fake.spawnFakePlayer(world, name, location)
        return FakePlayerImpl(player)
    }

    // ── Mobs ──────────────────────────────────────────────────────────────────

    /**
     * Spawn a fake mob of [entityType] at [location] with optional [configure] block.
     */
    fun spawnMob(
        world: World,
        entityType: EntityType,
        location: Location,
        configure: FakeMob.() -> Unit = {},
    ): FakeMob {
        val entity = Arc.nms.fake.spawnFakeMob(world, entityType, location) as? Mob
            ?: error("[Arc/Fake] EntityType ${entityType.name} is not a Mob")
        return FakeMobImpl(entity).also(configure)
    }

    /**
     * Spawn a fake mob of type [T] at [location] with optional [configure] block.
     *
     * ```kotlin
     * val skeleton = FakeServer.spawnMob<Skeleton>(world, loc) {
     *     equip(ItemStack(Material.BOW))
     * }
     * ```
     */
    inline fun <reified T : Mob> spawnMob(
        world: World,
        location: Location,
        noinline configure: FakeMob.() -> Unit = {},
    ): FakeMob {
        val type = EntityType.entries.first { it.entityClass == T::class.java }
        return spawnMob(world, type, location, configure)
    }

    // ── Session ───────────────────────────────────────────────────────────────

    /**
     * Run [block] inside a [FakeSession] anchored at [location], then automatically
     * remove all spawned fake entities — even if [block] throws.
     */
    inline fun session(world: World, location: Location, block: FakeSession.() -> Unit) {
        val session = FakeSession(world, location)
        try {
            session.block()
        } finally {
            session.cleanup()
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** True if [entity] is a fake player created by this system. */
    fun isFakePlayer(entity: Entity): Boolean = Arc.nms.fake.isFakePlayer(entity)

    /** True if [entity] is a fake mob created by this system. */
    fun isFakeMob(entity: Entity): Boolean = Arc.nms.fake.isFakeMob(entity)

    /** True if [entity] is any fake entity (player or mob) created by this system. */
    fun isFake(entity: Entity): Boolean = isFakePlayer(entity) || isFakeMob(entity)
}
