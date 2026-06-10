package dev.arc.api.packet

import org.bukkit.entity.Player

/**
 * A hook over a player's raw packet stream. Implement either callback (default = pass through unchanged):
 *  - return the (possibly **modified**) packet to forward it,
 *  - return a **different** packet object to swap it,
 *  - return `null` to **cancel/drop** it.
 *
 * Packets are raw NMS objects (`net.minecraft.network.protocol.*`); use [dev.arc.api.nms.NmsRef] to read or
 * tweak their fields. Callbacks run on Netty's event-loop thread - do not touch the Bukkit world directly
 * from here; hop to the main thread (e.g. a coroutine main dispatcher) if you must.
 */
public interface PacketInterceptor {

    /** Called for every clientbound (server → client) packet. */
    public fun onSend(player: Player, packet: Any): Any? = packet

    /** Called for every serverbound (client → server) packet. */
    public fun onReceive(player: Player, packet: Any): Any? = packet
}
