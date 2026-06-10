package dev.arc.api.packet

import org.bukkit.entity.Player
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference

/**
 * Packet interception facade. Requires arc-server (which provides the Netty [PacketBridge] via ServiceLoader);
 * no-ops on plain Paper.
 *
 * ```kotlin
 * // hide other players' chat from this player, and log their movement:
 * player.interceptPackets(
 *     onSend = { _, packet -> if (packet.javaClass.simpleName.contains("Chat")) null else packet },
 *     onReceive = { p, packet -> logger.info("${p.name} -> ${packet.javaClass.simpleName}"); packet },
 * )
 * ```
 */
public object Packets {

    private val ref = AtomicReference<PacketBridge?>(null)

    private fun bridge(): PacketBridge? = ref.get() ?: run {
        val loaded = ServiceLoader.load(PacketBridge::class.java, javaClass.classLoader).firstOrNull() ?: return null
        if (ref.compareAndSet(null, loaded)) loaded else ref.get()
    }

    /** Whether packet interception is available (arc-server present). */
    public val isAvailable: Boolean
        get() = bridge() != null

    /** Install [interceptor] on [player]. */
    public fun intercept(player: Player, interceptor: PacketInterceptor) {
        bridge()?.inject(player, interceptor)
    }

    /** Remove arc's interceptor from [player]. */
    public fun stopIntercepting(player: Player) {
        bridge()?.uninject(player)
    }
}

/** Intercept this player's packets with a full [PacketInterceptor]. */
public fun Player.interceptPackets(interceptor: PacketInterceptor) {
    Packets.intercept(this, interceptor)
}

/** Intercept this player's packets with inline lambdas (return the packet to forward, `null` to drop). */
public fun Player.interceptPackets(
    onSend: (Player, Any) -> Any? = { _, packet -> packet },
    onReceive: (Player, Any) -> Any? = { _, packet -> packet },
) {
    Packets.intercept(
        this,
        object : PacketInterceptor {
            override fun onSend(player: Player, packet: Any): Any? = onSend(player, packet)
            override fun onReceive(player: Player, packet: Any): Any? = onReceive(player, packet)
        },
    )
}

/** Stop intercepting this player's packets. */
public fun Player.stopInterceptingPackets() {
    Packets.stopIntercepting(this)
}
