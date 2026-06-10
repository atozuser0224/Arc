package dev.arc.api.fake

import dev.arc.api.Arc
import org.bukkit.Location
import org.bukkit.World

/**
 * Entry point for spawning and managing fake server-side players.
 *
 * Fake players are backed by real NMS `ServerPlayer` instances with a no-op
 * Netty channel. Every combat action — melee, projectile, damage — runs through
 * the full NMS pipeline, so plugins receive the same Bukkit events they would
 * from a real player (including correct `damager instanceof Player` checks,
 * armor reduction, knockback, sweep attacks, and death events).
 *
 * Requires `arc:nms.fake` feature flag to be enabled (default: on).
 *
 * ```kotlin
 * // One-off
 * val bot = FakeServer.spawn(world, "DummyBot", location)
 * bot.equip(ItemStack(Material.DIAMOND_SWORD))
 * val result = bot.attack(realPlayer)
 * bot.remove()
 *
 * // Auto-cleanup session
 * FakeServer.session(world, arenaCenter) {
 *     val attacker = spawn("Bot_A")
 *     val defender = spawn("Bot_B", arenaCenter.clone().add(3.0, 0.0, 0.0))
 *     attacker.attack(defender.player)
 * }
 * ```
 */
object FakeServer {

    /**
     * Spawn a fake NMS player in [world] at [location] with the given [name].
     * The caller is responsible for calling [FakePlayer.remove] when done.
     */
    fun spawn(world: World, name: String, location: Location): FakePlayer {
        val player = Arc.nms.fake.spawnFakePlayer(world, name, location)
        return FakePlayerImpl(player)
    }

    /**
     * Run [block] inside a [FakeSession] anchored at [location], then clean up
     * all spawned fake players automatically — even if [block] throws.
     */
    inline fun session(world: World, location: Location, block: FakeSession.() -> Unit) {
        val session = FakeSession(world, location)
        try {
            session.block()
        } finally {
            session.cleanup()
        }
    }

    /** True if [player] is a fake entity created by this system. */
    fun isFake(player: org.bukkit.entity.Entity): Boolean =
        Arc.nms.fake.isFakePlayer(player)
}
