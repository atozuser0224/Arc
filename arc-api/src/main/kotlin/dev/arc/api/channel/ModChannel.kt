@file:JvmName("ModChannels")

package dev.arc.api.channel

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Type-safe bidirectional channel for communicating with client mods (Fabric, NeoForge, etc.).
 *
 * The underlying protocol uses Minecraft's custom payload packet system with a
 * `ResourceLocation`-keyed channel. Modern Fabric/NeoForge mods send
 * `minecraft:register` on join — check [Player.hasModChannel] before sending.
 *
 * ```kotlin
 * // Define a channel once (share between server plugin and client mod)
 * val SYNC_CHANNEL = plugin.modChannel<SyncPacket>("mymod:sync") {
 *     encoder = { pkt, buf -> buf.writeUtf(pkt.value); buf.writeInt(pkt.count) }
 *     decoder = { buf -> SyncPacket(buf.readUtf(), buf.readInt()) }
 *     onReceive = { player, pkt -> handleSync(player, pkt) }
 * }
 *
 * // Send to a client that has the mod
 * if (player.hasModChannel("mymod:sync")) {
 *     SYNC_CHANNEL.send(player, SyncPacket("phase", 3))
 * }
 * ```
 *
 * `SyncPacket` is your own data class — anything serializable via FriendlyByteBuf.
 */
class ModChannel<T : Any> internal constructor(
    val plugin: Plugin,
    val channel: String,
    private val encoder: (T, FriendlyByteBuf) -> Unit,
    private val decoder: (FriendlyByteBuf) -> T,
    private val onReceive: ((Player, T) -> Unit)?,
) {
    init {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, channel)
        if (onReceive != null) {
            plugin.server.messenger.registerIncomingPluginChannel(plugin, channel,
                PluginMessageListener { _, player, message ->
                    val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(message))
                    try {
                        onReceive.invoke(player, decoder(buf))
                    } finally {
                        buf.release()
                    }
                }
            )
        }
    }

    /** Send [packet] to [player]. No-op if the player doesn't have the channel. */
    fun send(player: Player, packet: T) {
        if (!player.hasModChannel(channel)) return
        val buf = FriendlyByteBuf(Unpooled.buffer())
        try {
            encoder(packet, buf)
            val bytes = ByteArray(buf.readableBytes())
            buf.readBytes(bytes)
            player.sendPluginMessage(plugin, channel, bytes)
        } finally {
            buf.release()
        }
    }

    /** Broadcast [packet] to all online players that have the channel registered. */
    fun broadcast(packet: T) {
        plugin.server.onlinePlayers
            .filter { it.hasModChannel(channel) }
            .forEach { send(it, packet) }
    }

    /** Unregister this channel. Call on plugin disable. */
    fun close() {
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, channel)
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, channel)
    }
}

/** Builder DSL for [ModChannel]. */
class ModChannelBuilder<T : Any> {
    var encoder: ((T, FriendlyByteBuf) -> Unit)? = null
    var decoder: ((FriendlyByteBuf) -> T)? = null
    var onReceive: ((Player, T) -> Unit)? = null
}

/**
 * Create and register a typed mod channel on this plugin.
 *
 * [channel] should follow the `namespace:path` format used by mod APIs (e.g., `"mymod:sync"`).
 */
fun <T : Any> Plugin.modChannel(channel: String, block: ModChannelBuilder<T>.() -> Unit): ModChannel<T> {
    val builder = ModChannelBuilder<T>().apply(block)
    return ModChannel(
        plugin = this,
        channel = channel,
        encoder = builder.encoder ?: error("ModChannel '$channel' missing encoder"),
        decoder = builder.decoder ?: error("ModChannel '$channel' missing decoder"),
        onReceive = builder.onReceive,
    )
}

/**
 * Create a send-only mod channel (no incoming handler).
 * Useful for pushing server state to client mods.
 */
fun <T : Any> Plugin.modChannelOut(
    channel: String,
    encoder: (T, FriendlyByteBuf) -> Unit,
): ModChannel<T> = ModChannel(
    plugin = this,
    channel = channel,
    encoder = encoder,
    decoder = { error("send-only channel") },
    onReceive = null,
)

/** Whether this player's client has registered [channel] (i.e., the mod is installed). */
fun Player.hasModChannel(channel: String): Boolean = channel in listeningPluginChannels

/** All mod channel IDs this player's client has registered. */
val Player.modChannels: Set<String> get() = listeningPluginChannels
