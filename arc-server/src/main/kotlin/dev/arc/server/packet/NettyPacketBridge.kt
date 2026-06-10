package dev.arc.server.packet

import dev.arc.api.packet.PacketBridge
import dev.arc.api.packet.PacketInterceptor
import dev.arc.server.nms.Reflect
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.bukkit.entity.Player

/**
 * Netty-pipeline [PacketBridge]: installs a [ChannelDuplexHandler] just before the server's `packet_handler`
 * so a [PacketInterceptor] sees every clientbound (write) and serverbound (channelRead) packet, and may
 * modify or drop it.
 *
 * The channel is resolved reflectively (`ServerPlayer.connection.connection.channel`) and pipeline edits run
 * on the channel's own event loop for thread-safety. Registered via `META-INF/services`.
 */
public class NettyPacketBridge : PacketBridge {

    private companion object {
        const val HANDLER_NAME = "arc_packet_interceptor"
        const val MINECRAFT_HANDLER = "packet_handler"
    }

    override fun inject(player: Player, interceptor: PacketInterceptor) {
        val channel = channelOf(player) ?: return
        channel.eventLoop().execute {
            val pipeline = channel.pipeline()
            if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME)
            if (pipeline.get(MINECRAFT_HANDLER) != null) {
                pipeline.addBefore(MINECRAFT_HANDLER, HANDLER_NAME, InterceptHandler(player, interceptor))
            }
        }
    }

    override fun uninject(player: Player) {
        val channel = channelOf(player) ?: return
        channel.eventLoop().execute {
            val pipeline = channel.pipeline()
            if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME)
        }
    }

    // ServerPlayer.connection (ServerGamePacketListenerImpl) -> .connection (Connection) -> .channel (Channel)
    private fun channelOf(player: Player): Channel? {
        val serverPlayer = Reflect.handle(player) ?: return null
        val listener = Reflect.field(serverPlayer, "connection") ?: return null
        val connection = Reflect.field(listener, "connection") ?: return null
        return Reflect.field(connection, "channel") as? Channel
    }
}

private class InterceptHandler(
    private val player: Player,
    private val interceptor: PacketInterceptor,
) : ChannelDuplexHandler() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val result = try {
            interceptor.onSend(player, msg)
        } catch (_: Throwable) {
            msg
        }
        if (result == null) {
            promise.setSuccess()
        } else {
            super.write(ctx, result, promise)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val result = try {
            interceptor.onReceive(player, msg)
        } catch (_: Throwable) {
            msg
        }
        if (result != null) {
            super.channelRead(ctx, result)
        }
    }
}
