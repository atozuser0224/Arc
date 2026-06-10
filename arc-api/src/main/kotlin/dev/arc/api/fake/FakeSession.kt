package dev.arc.api.fake

import org.bukkit.Location
import org.bukkit.World

/**
 * Scoped context managing a group of [FakePlayer]s for the duration of a
 * simulation block. All spawned fakes are automatically [remove]d when the
 * session ends — even if the block throws.
 *
 * Obtain via [FakeServer.session].
 *
 * ```kotlin
 * FakeServer.session(world, center) {
 *     val a = spawn("Attacker")
 *     val b = spawn("Defender", center.clone().add(2.0, 0.0, 0.0))
 *     repeat(5) { a.attack(b.player) }
 *     println("defender hp: ${b.health}")
 * }
 * ```
 */
class FakeSession internal constructor(
    private val world: World,
    private val defaultLocation: Location,
) {
    private val players = mutableListOf<FakePlayer>()

    /** Spawn a new fake player at [location] (defaults to the session's base location). */
    fun spawn(name: String, location: Location = defaultLocation): FakePlayer =
        FakeServer.spawn(world, name, location).also { players += it }

    /** Remove all fake players created in this session. Called automatically by [FakeServer.session]. */
    fun cleanup() {
        players.forEach(FakePlayer::remove)
        players.clear()
    }

    /** Snapshot of all fake players currently in this session. */
    val all: List<FakePlayer> get() = players.toList()
}
