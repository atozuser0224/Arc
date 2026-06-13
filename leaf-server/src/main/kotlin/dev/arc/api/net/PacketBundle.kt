@file:JvmName("PacketBundles")

package dev.arc.api.net

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * Coalesce multiple outgoing packets into a single [ClientboundBundlePacket] frame.
 *
 * The vanilla client processes all packets in a bundle atomically before rendering.
 * This eliminates visual glitches when multiple related updates must arrive together
 * (e.g. teleport + velocity + metadata, or bulk entity spawns, or chunk + light updates).
 * It also reduces TCP overhead during burst events like explosions or arena resets.
 *
 * ```kotlin
 * // Send 3 packets atomically — client sees them all at once
 * player.sendBundle {
 *     +ClientboundTeleportEntityPacket(...)
 *     +ClientboundSetEntityMotionPacket(...)
 *     +ClientboundSetEntityDataPacket(...)
 * }
 *
 * // Send the same bundle to multiple players
 * val bundle = packetBundle {
 *     +explosionPacket
 *     +soundPacket
 * }
 * bundle.sendTo(player1, player2, player3)
 * ```
 */

/** DSL scope for collecting packets into a bundle. */
class PacketBundleScope {
    @PublishedApi
    internal val packets = mutableListOf<Packet<ClientGamePacketListener>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Packet<*>> T.unaryPlus() {
        packets += this as Packet<ClientGamePacketListener>
    }
}

/** Build a [ClientboundBundlePacket] from the packets collected in [block]. */
fun packetBundle(block: PacketBundleScope.() -> Unit): ClientboundBundlePacket {
    val scope = PacketBundleScope().apply(block)
    return ClientboundBundlePacket(scope.packets)
}

/** Send all packets in [block] to this player as a single atomic bundle. */
fun Player.sendBundle(block: PacketBundleScope.() -> Unit) {
    val bundle = packetBundle(block)
    (this as CraftPlayer).handle.connection.send(bundle)
}

/** Send a pre-built [ClientboundBundlePacket] to each player in [players]. */
fun ClientboundBundlePacket.sendTo(vararg players: Player) {
    for (p in players) (p as CraftPlayer).handle.connection.send(this)
}

/** Wrap arbitrary packets in a bundle and send to [players]. */
fun sendBundleTo(vararg players: Player, block: PacketBundleScope.() -> Unit) {
    val bundle = packetBundle(block)
    for (p in players) (p as CraftPlayer).handle.connection.send(bundle)
}
