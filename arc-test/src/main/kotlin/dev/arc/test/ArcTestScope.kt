@file:JvmName("ArcTests")

package dev.arc.test

import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import dev.arc.api.db.ArcDatabase
import dev.arc.test.assert.EventCapture
import dev.arc.test.clock.TickClock
import dev.arc.test.db.TestDatabase
import dev.arc.test.extra.BenchmarkScope
import dev.arc.test.extra.FuzzScope
import dev.arc.test.extra.PermissionAudit
import dev.arc.test.extra.WorldSnapshot
import dev.arc.test.virtual.VirtualConsole
import dev.arc.test.virtual.VirtualNetwork
import dev.arc.test.virtual.VirtualPlayer
import dev.arc.test.virtual.virtualNetwork
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.bukkit.plugin.Plugin

/**
 * DSL scope available inside [arcTest].
 * Provides player creation, tick control, DB injection, event capture,
 * and advanced tooling (fuzz, benchmark, snapshot, etc.).
 */
class ArcTestScope internal constructor(
    /** The test plugin instance loaded into MockBukkit. */
    val plugin: Plugin,
    private val server: ServerMock,
    /** Unified tick controller (Bukkit scheduler + coroutine time). */
    val clock: TickClock,
    private val coroutineScheduler: TestCoroutineScheduler,
) {
    /** Default test world. */
    val world: WorldMock by lazy { server.addSimpleWorld("world") }

    /** Event capture — every event fired on this server is recorded here. */
    val events: EventCapture = EventCapture()

    /** Lazy in-memory database — created on first access. */
    val db: ArcDatabase by lazy { TestDatabase.create() }

    /** Console sender — run admin commands or assert console output. */
    val console: VirtualConsole by lazy { VirtualConsole(server) }

    // ---- Player factory ----

    /**
     * Create a [VirtualPlayer] with optional initialisation block.
     *
     * ```kotlin
     * val player = withPlayer("Steve") { grant("myplugin.admin") }
     * ```
     */
    fun withPlayer(name: String, block: VirtualPlayer.() -> Unit = {}): VirtualPlayer {
        val mock = server.addPlayer(name)
        return VirtualPlayer(mock, plugin).apply(block)
    }

    /**
     * Create multiple [VirtualPlayer]s at once.
     *
     * ```kotlin
     * val (alice, bob) = withPlayers("Alice", "Bob")
     * ```
     */
    fun withPlayers(vararg names: String): List<VirtualPlayer> = names.map { withPlayer(it) }

    // ---- Time control ----

    /** Advance the server by [ticks] ticks (Bukkit scheduler + coroutine delays). */
    fun tick(ticks: Int = 1) = clock.advance(ticks)

    /** Advance by [seconds] real-time seconds (1s = 20 ticks). */
    fun seconds(s: Int) = clock.advanceSeconds(s)

    // ---- Multi-server ----

    /**
     * Build a [VirtualNetwork] of multiple mock servers sharing a message bus.
     *
     * ```kotlin
     * val net = virtualNetwork {
     *     server("lobby")
     *     server("survival")
     * }
     * ```
     */
    fun virtualNetwork(block: dev.arc.test.virtual.VirtualNetworkBuilder.() -> Unit): VirtualNetwork =
        dev.arc.test.virtual.virtualNetwork(coroutineScheduler, block)

    // ---- Advanced tooling ----

    /** Capture world block state before/after an operation for diff assertions. */
    fun snapshot(block: WorldSnapshot.() -> Unit) = WorldSnapshot(world).apply(block)

    /** Run fuzzing iterations — catch crashes on random command/input. */
    fun fuzz(iterations: Int = 50, block: FuzzScope.(String) -> Unit) =
        FuzzScope(plugin).run(iterations, block)

    /** Assert a code block completes within [limitMs] milliseconds. */
    suspend fun assertUnder(limitMs: Long, block: suspend () -> Unit) =
        BenchmarkScope.assertUnder(limitMs, block)

    /** Scan all registered commands for missing permission nodes. */
    fun auditPermissions(): List<String> = PermissionAudit.scan(server)
}

// ---------------------------------------------------------------------------
//  Top-level entry point
// ---------------------------------------------------------------------------

/**
 * Run a server-free Arc plugin test. Sets up MockBukkit automatically
 * if not already configured (i.e. when used without [@ArcTest][dev.arc.test.annotation.ArcTest]).
 *
 * ```kotlin
 * @Test
 * fun `coins deducted on purchase`() = arcTest {
 *     val player = withPlayer("Steve") { grant("shop.buy") }
 *     player.performCommand("shop buy sword")
 *     tick(1)
 *     player.assertMessage("구매 완료!")
 *     player.assertInventoryContains(Material.DIAMOND_SWORD)
 * }
 * ```
 */
fun arcTest(block: suspend ArcTestScope.() -> Unit): Unit = runTest {
    val alreadyMocked = runCatching { MockBukkit.getMock() }.isSuccess
    val server: ServerMock = if (alreadyMocked) MockBukkit.getMock() as ServerMock else MockBukkit.mock()
    val plugin: TestPlugin = MockBukkit.load(TestPlugin::class.java)
    val clock = TickClock(server, testScheduler)
    try {
        ArcTestScope(plugin, server, clock, testScheduler).block()
    } finally {
        if (!alreadyMocked) runCatching { MockBukkit.unmock() }
    }
}
