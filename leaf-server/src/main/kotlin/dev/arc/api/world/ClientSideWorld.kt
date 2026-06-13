@file:JvmName("ClientSideBlocks")

package dev.arc.api.world

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.craftbukkit.block.data.CraftBlockData
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player fake block layer. Sends synthetic block-update packets so specific
 * players see different blocks than the real world state — without touching
 * server-side block data.
 *
 * Classic use-cases:
 * - Team-coloured barrier walls (allies see air, enemies see obsidian)
 * - Hidden stairways or traps
 * - Per-player "illusion" blocks in puzzle/escape rooms
 *
 * ```kotlin
 * val csw = ClientSideWorld()
 *
 * // Player A sees a glass wall; no one else does
 * csw.setBlock(location, Material.GLASS.createBlockData(), playerA)
 *
 * // Restore the real world block for player A
 * csw.clearBlock(location, playerA)
 *
 * // When a player re-joins, resend all their fake blocks
 * csw.resync(playerA)
 * ```
 */
class ClientSideWorld {

    // viewerUUID → (encodedPos → blockData)
    private val overrides = ConcurrentHashMap<UUID, ConcurrentHashMap<Long, BlockData>>()

    // ── Write ──────────────────────────────────────────────────────────────────

    /** Make [viewers] see [data] at [location] instead of the real block. */
    fun setBlock(location: Location, data: BlockData, vararg viewers: Player) {
        val pos = location.toBlockPos()
        val key = pos.asLong()
        for (viewer in viewers) {
            overrides.getOrPut(viewer.uniqueId) { ConcurrentHashMap() }[key] = data
            sendUpdate(viewer, pos, data)
        }
    }

    /** Convenience overload accepting a [Material]. */
    fun setBlock(location: Location, material: Material, vararg viewers: Player) =
        setBlock(location, material.createBlockData(), *viewers)

    /** Restore the real world block at [location] for [viewers]. */
    fun clearBlock(location: Location, vararg viewers: Player) {
        val pos = location.toBlockPos()
        val key = pos.asLong()
        val realData = location.block.blockData
        for (viewer in viewers) {
            overrides[viewer.uniqueId]?.remove(key)
            sendUpdate(viewer, pos, realData)
        }
    }

    /** Clear all fake blocks for [viewer] and resend all real blocks. */
    fun clearAll(viewer: Player) {
        val map = overrides.remove(viewer.uniqueId) ?: return
        map.forEach { (encodedPos, _) ->
            val pos = BlockPos.of(encodedPos)
            val loc = Location(viewer.world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            sendUpdate(viewer, pos, loc.block.blockData)
        }
    }

    /** Resend all fake blocks for [viewer] (call on player join/respawn). */
    fun resync(viewer: Player) {
        val map = overrides[viewer.uniqueId] ?: return
        map.forEach { (encodedPos, data) ->
            sendUpdate(viewer, BlockPos.of(encodedPos), data)
        }
    }

    /** Whether [viewer] has any fake block override active. */
    fun hasOverrides(viewer: Player): Boolean =
        overrides[viewer.uniqueId]?.isNotEmpty() == true

    /** Get the fake block data [viewer] sees at [location], or null if none. */
    fun getOverride(viewer: Player, location: Location): BlockData? =
        overrides[viewer.uniqueId]?.get(location.toBlockPos().asLong())

    /** Number of active fake block overrides for [viewer]. */
    fun overrideCount(viewer: Player): Int =
        overrides[viewer.uniqueId]?.size ?: 0

    // ── Batch helpers ──────────────────────────────────────────────────────────

    /**
     * Apply a batch of fake blocks for [viewers] defined in [block].
     * More efficient than individual [setBlock] calls within the same chunk section.
     *
     * ```kotlin
     * csw.batch(playerA, playerB) {
     *     set(loc1, Material.OBSIDIAN)
     *     set(loc2, Material.AIR)
     * }
     * ```
     */
    fun batch(vararg viewers: Player, block: BatchScope.() -> Unit) {
        val scope = BatchScope()
        scope.block()
        scope.entries.forEach { (loc, data) -> setBlock(loc, data, *viewers) }
    }

    inner class BatchScope {
        internal val entries = mutableListOf<Pair<Location, BlockData>>()
        fun set(location: Location, data: BlockData) { entries += location to data }
        fun set(location: Location, material: Material) = set(location, material.createBlockData())
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun sendUpdate(viewer: Player, pos: BlockPos, data: BlockData) {
        val nmsState = (data as CraftBlockData).state
        val packet = ClientboundBlockUpdatePacket(pos, nmsState)
        (viewer as CraftPlayer).handle.connection.send(packet)
    }

    private fun Location.toBlockPos() = BlockPos(blockX, blockY, blockZ)
}

/** Singleton convenience instance. */
val GlobalClientSideWorld = ClientSideWorld()
