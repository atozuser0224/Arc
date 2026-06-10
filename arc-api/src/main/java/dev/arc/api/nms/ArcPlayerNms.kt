package dev.arc.api.nms

import dev.arc.api.scheduling.callEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/** Player/connection capabilities that require server internals (NMS). */
interface ArcPlayerNms {

    /** Send a raw NMS packet object straight to [player]'s connection. */
    fun sendPacket(player: Player, packet: Any)

    /** The player's connection latency in ms (`ServerPlayer#latency`). */
    fun ping(player: Player): Int

    /** The player's protocol version, or -1 if it can't be determined. */
    fun protocolVersion(player: Player): Int

    /** The `ServerGamePacketListenerImpl` connection (escape hatch). */
    fun connection(player: Player): Any?

    /** The raw `net.minecraft.server.level.ServerPlayer` handle (escape hatch). */
    fun handle(player: Player): Any?

    /** Tell [player]'s client to remove the entities with the given network ids. */
    fun hideEntities(player: Player, vararg entityIds: Int)

    /** The player's authlib `GameProfile` (escape hatch — skin/textures live here). */
    fun gameProfile(player: Player): Any?

    /**
     * Set a property on the player's [GameProfile][gameProfile] (e.g. `textures`
     * for a custom skin). Takes effect on the client only after a re-login or a
     * respawn/entity refresh.
     */
    fun setProfileProperty(player: Player, name: String, value: String, signature: String?)

    /** Re-send the chunk at [chunkX], [chunkZ] to [player] (force client refresh). */
    fun resendChunk(player: Player, chunkX: Int, chunkZ: Int): Boolean

    /** Send a packet on the player's owning entity tick thread. */
    fun sendPacketAsync(plugin: Plugin, player: Player, packet: Any): CompletableFuture<Unit> =
        plugin.callEntity(player) { sendPacket(player, packet) }

    fun pingAsync(plugin: Plugin, player: Player): CompletableFuture<Int> =
        plugin.callEntity(player) { ping(player) }

    fun protocolVersionAsync(plugin: Plugin, player: Player): CompletableFuture<Int> =
        plugin.callEntity(player) { protocolVersion(player) }

    fun hideEntitiesAsync(
        plugin: Plugin,
        player: Player,
        vararg entityIds: Int,
    ): CompletableFuture<Unit> = plugin.callEntity(player) { hideEntities(player, *entityIds) }

    fun setProfilePropertyAsync(
        plugin: Plugin,
        player: Player,
        name: String,
        value: String,
        signature: String?,
    ): CompletableFuture<Unit> = plugin.callEntity(player) {
        setProfileProperty(player, name, value, signature)
    }

    /** Build on the chunk region, then send on the player's entity thread. */
    fun resendChunkAsync(
        plugin: Plugin,
        player: Player,
        chunkX: Int,
        chunkZ: Int,
    ): CompletableFuture<Boolean> = plugin.callEntity(player) { resendChunk(player, chunkX, chunkZ) }
}
