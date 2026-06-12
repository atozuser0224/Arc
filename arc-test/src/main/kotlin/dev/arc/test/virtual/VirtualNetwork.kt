@file:JvmName("VirtualNetworks")

package dev.arc.test.virtual

import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import dev.arc.test.TestPlugin
import dev.arc.test.clock.TickClock
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
//  In-memory message bus
// ---------------------------------------------------------------------------

typealias MessageHandler = (channel: String, player: UUID?, data: ByteArray) -> Unit

/**
 * In-memory replacement for Redis/BungeeCord messaging channels.
 * All registered servers share the same bus instance.
 */
class VirtualMessageBus {
    private val subscriptions = ConcurrentHashMap<String, MutableList<MessageHandler>>()

    fun subscribe(channel: String, handler: MessageHandler) {
        subscriptions.getOrPut(channel) { mutableListOf() } += handler
    }

    fun publish(channel: String, player: UUID? = null, data: ByteArray = ByteArray(0)) {
        subscriptions[channel]?.forEach { it(channel, player, data) }
    }

    fun clear() { subscriptions.clear() }
}

// ---------------------------------------------------------------------------
//  Single server node in the network
// ---------------------------------------------------------------------------

class VirtualServerNode internal constructor(
    val name: String,
    internal val server: ServerMock,
    internal val plugin: TestPlugin,
    internal val bus: VirtualMessageBus,
    internal val clock: TickClock,
) {
    /** Add a virtual player to this server. */
    fun withPlayer(playerName: String, block: VirtualPlayer.() -> Unit = {}): VirtualPlayer {
        val mock = server.addPlayer(playerName)
        return VirtualPlayer(mock, plugin).apply(block)
    }

    /** Find a virtual player on this server by UUID, or null. */
    fun playerOf(uuid: UUID): VirtualPlayer? {
        val mock = server.getPlayer(uuid) as? org.mockbukkit.mockbukkit.entity.PlayerMock ?: return null
        return VirtualPlayer(mock, plugin)
    }

    /** Send a plugin-channel message as if it arrived from the proxy. */
    fun sendPluginMessage(channel: String, player: UUID? = null, data: ByteArray = ByteArray(0)) {
        bus.publish(channel, player, data)
    }

    /** Advance this server's tick clock by [ticks]. */
    fun tick(ticks: Int) = clock.advance(ticks)
}

// ---------------------------------------------------------------------------
//  Network DSL
// ---------------------------------------------------------------------------

class VirtualNetworkBuilder {
    internal val serverNames = mutableListOf<Pair<String, (VirtualServerNode.() -> Unit)>>()

    fun server(name: String, block: VirtualServerNode.() -> Unit = {}) {
        serverNames += name to block
    }
}

/**
 * A collection of [VirtualServerNode]s connected by an in-memory message bus.
 *
 * ```kotlin
 * val network = virtualNetwork {
 *     server("lobby")
 *     server("survival")
 * }
 * val player = network["lobby"].withPlayer("Steve")
 * player.transferTo(network, "survival")
 * ```
 */
class VirtualNetwork internal constructor(
    private val servers: Map<String, VirtualServerNode>,
    val bus: VirtualMessageBus,
) {
    operator fun get(name: String): VirtualServerNode =
        servers[name] ?: error("No server named '$name'. Available: ${servers.keys}")

    val serverNames: Set<String> get() = servers.keys

    /** Advance ALL servers by [ticks] ticks simultaneously. */
    fun tick(ticks: Int) = servers.values.forEach { it.tick(ticks) }

    /** Broadcast on the shared bus (simulates Redis pub/sub). */
    fun broadcast(channel: String, data: ByteArray = ByteArray(0)) =
        bus.publish(channel, null, data)
}

/** Simulate transferring this player to [targetServer] via proxy. */
fun VirtualPlayer.transferTo(network: VirtualNetwork, targetServer: String) {
    val uuid = uniqueId
    val target = network[targetServer]
    target.server.addPlayer(name)
    network.bus.publish("BungeeCord", uuid, "Connect $targetServer".toByteArray())
}

// ---------------------------------------------------------------------------
//  Entry point
// ---------------------------------------------------------------------------

/**
 * Build a [VirtualNetwork] with multiple mock servers sharing a message bus.
 * Call inside [arcTest][dev.arc.test.ArcTestScope] where MockBukkit is set up.
 */
fun virtualNetwork(
    coroutineScheduler: TestCoroutineScheduler,
    block: VirtualNetworkBuilder.() -> Unit,
): VirtualNetwork {
    val builder = VirtualNetworkBuilder().apply(block)
    val bus = VirtualMessageBus()
    val servers = mutableMapOf<String, VirtualServerNode>()

    for ((name, configure) in builder.serverNames) {
        val server = MockBukkit.getMock() as ServerMock
        val plugin = MockBukkit.load(TestPlugin::class.java)
        val clock = TickClock(server, coroutineScheduler)
        val node = VirtualServerNode(name, server, plugin, bus, clock)
        configure(node)
        servers[name] = node
    }

    return VirtualNetwork(servers, bus)
}
