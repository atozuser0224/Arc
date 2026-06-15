@file:JvmName("BungeeCord")

package dev.arc.api.messaging

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * Type-safe DSL for BungeeCord / Velocity proxy plugin messaging.
 *
 * The raw API requires manually constructing [ByteArrayDataOutput] streams and remembering
 * the BungeeCord sub-channel string for every operation. This wrapper exposes them as
 * named functions on [Player] and returns structured data via callbacks.
 *
 * ```kotlin
 * // In onEnable — register once:
 * val bungee = plugin.bungeeCord()
 *
 * // Move a player to another server:
 * bungee.connect(player, "lobby")
 *
 * // Query online count on a server (async, result delivered on main thread):
 * bungee.playerCount("survival") { count ->
 *     player.sendMessage("Survival has $count players online")
 * }
 *
 * // In onDisable:
 * bungee.close()
 * ```
 *
 * Requires `bungeecord: true` (BungeeCord) or `velocity: true` (Velocity) in `paper.yml`.
 */
public class BungeeCordMessenger internal constructor(private val plugin: Plugin) : AutoCloseable {

    private val channel = "BungeeCord"
    private val callbacks = mutableMapOf<String, MutableList<(ByteArrayDataInput) -> Unit>>()
    private val listener: PluginMessageListener

    init {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, channel)
        listener = PluginMessageListener { _, _, message ->
            val input = ByteStreams.newDataInput(message)
            val subChannel = input.readUTF()
            callbacks[subChannel]?.forEach { it(input) }
        }
        plugin.server.messenger.registerIncomingPluginChannel(plugin, channel, listener)
    }

    /** Transfer [player] to [server]. */
    public fun connect(player: Player, server: String): Unit =
        send(player, "Connect") { writeUTF(server) }

    /** Transfer [playerName] (by name) to [server], using [via] as the relay player. */
    public fun connectOther(via: Player, playerName: String, server: String): Unit =
        send(via, "ConnectOther") { writeUTF(playerName); writeUTF(server) }

    /** Query the server [player] is currently on, delivered to [callback]. */
    public fun getServer(player: Player, callback: (String) -> Unit) {
        on("GetServer") { input -> callback(input.readUTF()) }
        send(player, "GetServer")
    }

    /** Query online player count on [server], delivered to [callback]. */
    public fun playerCount(server: String, via: Player = anyOnlinePlayer(), callback: (Int) -> Unit) {
        on("PlayerCount") { input ->
            val srv = input.readUTF()
            if (srv == server || server == "ALL") callback(input.readInt())
        }
        send(via, "PlayerCount") { writeUTF(server) }
    }

    /** Query the list of online players on [server], delivered to [callback]. */
    public fun playerList(server: String, via: Player = anyOnlinePlayer(), callback: (List<String>) -> Unit) {
        on("PlayerList") { input ->
            input.readUTF() // server name
            callback(input.readUTF().split(", "))
        }
        send(via, "PlayerList") { writeUTF(server) }
    }

    /** Send [message] to [player] via the proxy. */
    public fun message(player: Player, message: String): Unit =
        send(player, "Message") { writeUTF(player.name); writeUTF(message) }

    /** Get the UUID of a player by [name], delivered to [callback]. */
    public fun uuid(via: Player, name: String, callback: (java.util.UUID) -> Unit) {
        on("UUID") { input -> callback(java.util.UUID.fromString(input.readUTF())) }
        send(via, "UUID") { writeUTF(name) }
    }

    /** Raw send — build the payload with [block]. */
    public fun send(player: Player, subChannel: String, block: ByteArrayDataOutput.() -> Unit = {}) {
        val out: ByteArrayDataOutput = ByteStreams.newDataOutput()
        out.writeUTF(subChannel)
        out.block()
        player.sendPluginMessage(plugin, channel, out.toByteArray())
    }

    private fun on(subChannel: String, handler: (ByteArrayDataInput) -> Unit) {
        callbacks.getOrPut(subChannel) { mutableListOf() } += handler
    }

    private fun anyOnlinePlayer(): Player =
        Bukkit.getOnlinePlayers().firstOrNull()
            ?: error("No online players — cannot send BungeeCord plugin message")

    override fun close() {
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, channel)
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, channel, listener)
        callbacks.clear()
    }
}

/** Create and register a [BungeeCordMessenger] for this plugin. Call [BungeeCordMessenger.close] in `onDisable`. */
public fun Plugin.bungeeCord(): BungeeCordMessenger = BungeeCordMessenger(this)
