package dev.arc.api.fake

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob

/**
 * Scoped context managing a group of fake entities for the duration of a simulation
 * block. All spawned entities are automatically [remove]d when the session ends —
 * even if the block throws.
 *
 * Obtain via [FakeServer.session].
 *
 * ```kotlin
 * FakeServer.session(world, arenaCenter) {
 *     val player = spawnPlayer("Bot")
 *     val zombie = spawnMob<Zombie>()
 *     val skeleton = spawnMob(EntityType.SKELETON, arenaCenter.clone().add(3.0, 0.0, 0.0))
 *
 *     player.attack(zombie.entity)
 *     zombie.setTarget(player.entity)
 *
 *     println("zombie hp after hit: ${zombie.health}")
 * }
 * ```
 */
class FakeSession internal constructor(
    private val world: World,
    private val defaultLocation: Location,
) {
    private val fakes = mutableListOf<FakeEntity>()

    // ── Spawn helpers ─────────────────────────────────────────────────────────

    /** Spawn a fake player at [location] (defaults to the session's anchor). */
    fun spawnPlayer(name: String, location: Location = defaultLocation): FakePlayer =
        FakeServer.spawn(world, name, location).also { fakes += it }

    /** Spawn a fake mob of [type] at [location] with optional [configure] block. */
    fun spawnMob(
        type: EntityType,
        location: Location = defaultLocation,
        configure: FakeMob.() -> Unit = {},
    ): FakeMob = FakeServer.spawnMob(world, type, location, configure).also { fakes += it }

    /** Spawn a fake mob of type [T] at [location] with optional [configure] block. */
    inline fun <reified T : Mob> spawnMob(
        location: Location = defaultLocation,
        noinline configure: FakeMob.() -> Unit = {},
    ): FakeMob = FakeServer.spawnMob<T>(world, location, configure).also { fakes += it }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Remove all entities created in this session. Called automatically by [FakeServer.session]. */
    fun cleanup() {
        fakes.forEach(FakeEntity::remove)
        fakes.clear()
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    val all: List<FakeEntity>   get() = fakes.toList()
    val players: List<FakePlayer> get() = fakes.filterIsInstance<FakePlayer>()
    val mobs: List<FakeMob>     get() = fakes.filterIsInstance<FakeMob>()
}
