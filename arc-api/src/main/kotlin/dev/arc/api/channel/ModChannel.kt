@file:JvmName("ModChannels")

package dev.arc.api.channel

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener

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
 *     encoder = { pkt, buf -> buf.writeUtf(pkt.value).writeInt(pkt.count) }
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
 * `SyncPacket` is your own data class. [ModPacketBuffer] keeps this public API
 * independent from server internals while retaining Minecraft-compatible
 * VarInt-prefixed UTF and byte-array encodings.
 */
public class ModChannel<T : Any> internal constructor(
    public val plugin: Plugin,
    public val channel: String,
    private val encoder: (T, ModPacketBuffer) -> Unit,
    private val decoder: (ModPacketBuffer) -> T,
    private val onReceive: ((Player, T) -> Unit)?,
) : AutoCloseable {
    private var closed: Boolean = false

    init {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, channel)
        if (onReceive != null) {
            plugin.server.messenger.registerIncomingPluginChannel(plugin, channel,
                PluginMessageListener { _, player, message ->
                    onReceive.invoke(player, decoder(ModPacketBuffer.reading(message)))
                }
            )
        }
    }

    /** Send [packet] to [player]. No-op if the player doesn't have the channel. */
    public fun send(player: Player, packet: T) {
        check(!closed) { "Mod channel '$channel' is closed" }
        if (!player.hasModChannel(channel)) return
        val buffer = ModPacketBuffer.writing()
        encoder(packet, buffer)
        player.sendPluginMessage(plugin, channel, buffer.toByteArray())
    }

    /** Broadcast [packet] to all online players that have the channel registered. */
    public fun broadcast(packet: T) {
        check(!closed) { "Mod channel '$channel' is closed" }
        plugin.server.onlinePlayers
            .filter { it.hasModChannel(channel) }
            .forEach { send(it, packet) }
    }

    /** Unregister this channel. Call on plugin disable. */
    override fun close() {
        if (closed) return
        closed = true
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, channel)
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, channel)
    }
}

/** Builder DSL for [ModChannel]. */
@DslMarker
public annotation class ModChannelDsl

@ModChannelDsl
public class ModChannelBuilder<T : Any> {
    public var encoder: ((T, ModPacketBuffer) -> Unit)? = null
    public var decoder: ((ModPacketBuffer) -> T)? = null
    public var onReceive: ((Player, T) -> Unit)? = null
}

/**
 * Create and register a typed mod channel on this plugin.
 *
 * [channel] should follow the `namespace:path` format used by mod APIs (e.g., `"mymod:sync"`).
 */
public fun <T : Any> Plugin.modChannel(
    channel: String,
    block: ModChannelBuilder<T>.() -> Unit,
): ModChannel<T> {
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
public fun <T : Any> Plugin.modChannelOut(
    channel: String,
    encoder: (T, ModPacketBuffer) -> Unit,
): ModChannel<T> = ModChannel(
    plugin = this,
    channel = channel,
    encoder = encoder,
    decoder = { error("send-only channel") },
    onReceive = null,
)

/** Whether this player's client has registered [channel] (i.e., the mod is installed). */
public fun Player.hasModChannel(channel: String): Boolean = channel in listeningPluginChannels

/** All mod channel IDs this player's client has registered. */
public val Player.modChannels: Set<String> get() = listeningPluginChannels
