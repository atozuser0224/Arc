package dev.arc.api.packet

import org.bukkit.entity.Player

/**
 * SPI implemented by arc-server: injects/removes a [PacketInterceptor] into a player's Netty pipeline.
 * Discovered via [ServiceLoader] by [Packets]. No-ops cleanly when absent.
 */
public interface PacketBridge {

    /** Install [interceptor] on [player]'s connection (replacing any previous arc interceptor). */
    public fun inject(player: Player, interceptor: PacketInterceptor)

    /** Remove arc's interceptor from [player]'s connection. */
    public fun uninject(player: Player)
}
