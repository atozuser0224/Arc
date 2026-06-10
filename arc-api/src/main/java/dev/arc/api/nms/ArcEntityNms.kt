package dev.arc.api.nms

import dev.arc.api.scheduling.callEntity
import dev.arc.api.scheduling.callGlobal
import dev.arc.api.scheduling.callRegion
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/** Entity capabilities that require server internals (NMS). */
interface ArcEntityNms {

    /** Full entity NBT serialized to an SNBT string (no Bukkit equivalent). */
    fun saveNbt(entity: Entity): String

    /** Apply an SNBT string onto [entity], overwriting its NBT. */
    fun loadNbt(entity: Entity, snbt: String)

    /** Age in ticks since spawn (`Entity#tickCount`). */
    fun ageTicks(entity: Entity): Int

    /** Simple name of the NMS handle class (e.g. `ServerPlayer`). */
    fun handleType(entity: Entity): String

    /** The entity's network/runtime id (`Entity#getId()`). */
    fun networkId(entity: Entity): Int

    /** Look up an entity by its network id within [world] (Bukkit only does UUID). */
    fun byId(world: World, id: Int): Entity?

    /**
     * Look up a network id among entities owned by one chunk region. This is the
     * region-safe alternative to [byId] when the entity's chunk is known.
     */
    fun byIdInChunk(world: World, chunkX: Int, chunkZ: Int, id: Int): Entity?

    /** The raw `net.minecraft.world.entity.Entity` handle (escape hatch). */
    fun handle(entity: Entity): Any?

    /**
     * Build a spawn packet (`ClientboundAddEntityPacket`) for [entity] so it can
     * be shown to specific players only (fake/client-side entities). Returns null
     * if it can't be built on this server. Send it via [ArcPlayerNms.sendPacket].
     */
    fun spawnPacket(entity: Entity): Any?

    /** Capture entity NBT on its owning entity tick thread. */
    fun saveNbtAsync(plugin: Plugin, entity: Entity): CompletableFuture<String> =
        plugin.callEntity(entity) { saveNbt(entity) }

    /** Apply SNBT on its owning entity tick thread. */
    fun loadNbtAsync(plugin: Plugin, entity: Entity, snbt: String): CompletableFuture<Unit> =
        plugin.callEntity(entity) { loadNbt(entity, snbt) }

    fun ageTicksAsync(plugin: Plugin, entity: Entity): CompletableFuture<Int> =
        plugin.callEntity(entity) { ageTicks(entity) }

    fun networkIdAsync(plugin: Plugin, entity: Entity): CompletableFuture<Int> =
        plugin.callEntity(entity) { networkId(entity) }

    fun spawnPacketAsync(plugin: Plugin, entity: Entity): CompletableFuture<Any?> =
        plugin.callEntity(entity) { spawnPacket(entity) }

    /**
     * Entity ids carry no location, so lookup is serialized through the global
     * scheduler. On regionized servers prefer [byIdInChunkAsync], because a
     * global task does not own every world's entity index.
     */
    fun byIdAsync(plugin: Plugin, world: World, id: Int): CompletableFuture<Entity?> =
        plugin.callGlobal { byId(world, id) }

    /** Look up [id] on the tick thread that owns the specified chunk. */
    fun byIdInChunkAsync(
        plugin: Plugin,
        world: World,
        chunkX: Int,
        chunkZ: Int,
        id: Int,
    ): CompletableFuture<Entity?> {
        val location = Location(
            world,
            (chunkX shl 4).toDouble(),
            world.minHeight.toDouble(),
            (chunkZ shl 4).toDouble(),
        )
        return plugin.callRegion(location) { byIdInChunk(world, chunkX, chunkZ, id) }
    }
}
