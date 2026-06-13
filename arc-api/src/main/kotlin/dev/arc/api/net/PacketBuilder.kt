@file:JvmName("Packets")

package dev.arc.api.net

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * Send an arbitrary NMS packet to this player.
 * Useful for protocol-level features not exposed by Bukkit:
 * virtual entity spawning, map data, custom sound IDs, etc.
 *
 * ```kotlin
 * val packet = ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.5f)
 * player.sendNmsPacket(packet)
 * ```
 *
 * Type parameter [T] must be [ClientGamePacketListener] — these are the client-bound
 * game-phase packets (play state). Login/configuration packets are not supported here.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ClientGamePacketListener> Player.sendNmsPacket(packet: Packet<T>) {
    (this as CraftPlayer).handle.connection.send(packet as Packet<ClientGamePacketListener>)
}

/**
 * Send multiple NMS packets to this player in sequence.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ClientGamePacketListener> Player.sendNmsPackets(vararg packets: Packet<T>) {
    val conn = (this as CraftPlayer).handle.connection
    packets.forEach { conn.send(it as Packet<ClientGamePacketListener>) }
}

/**
 * Fluent packet bundle — collect packets and flush to one or more players.
 *
 * ```kotlin
 * packetBatch {
 *     +ClientboundAddEntityPacket(...)
 *     +ClientboundSetEntityDataPacket(...)
 * }.sendTo(player1, player2)
 * ```
 */
class PacketBatch {
    @PublishedApi
    internal val packets = mutableListOf<Packet<ClientGamePacketListener>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : ClientGamePacketListener> Packet<T>.unaryPlus() {
        packets += this as Packet<ClientGamePacketListener>
    }

    fun sendTo(vararg players: Player) {
        players.forEach { player ->
            val conn = (player as CraftPlayer).handle.connection
            packets.forEach { conn.send(it) }
        }
    }
}

/** Build a batch of NMS packets. */
inline fun packetBatch(block: PacketBatch.() -> Unit): PacketBatch =
    PacketBatch().apply(block)
