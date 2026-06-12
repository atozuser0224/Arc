@file:JvmName("VirtualBlocks")

package dev.arc.api.block

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player fake block layer — show different block states to individual players
 * without touching the real world.  Backed by [Player.sendBlockChange].
 *
 * ```kotlin
 * val layer = VirtualBlockLayer(plugin)
 *
 * // Show a diamond block to one player at a location
 * layer.set(player, location, Material.DIAMOND_BLOCK.createBlockData())
 *
 * // Revert that player's view of the location to the real block
 * layer.clear(player, location)
 *
 * // Revert everything for a player (e.g., on quit)
 * layer.clearAll(player)
 * ```
 */
class VirtualBlockLayer(private val plugin: Plugin) {

    // player uuid -> location key -> BlockData
    private val fakeBlocks = ConcurrentHashMap<java.util.UUID, ConcurrentHashMap<Long, BlockData>>()

    /**
     * Show [data] at [location] to [player] only.
     * The real world block is untouched.
     */
    fun set(player: Player, location: Location, data: BlockData) {
        fakeBlocks
            .getOrPut(player.uniqueId) { ConcurrentHashMap() }
            .put(locationKey(location), data)
        player.sendBlockChange(location, data)
    }

    /** Convenience overload for a plain [material]. */
    fun set(player: Player, location: Location, material: Material) =
        set(player, location, material.createBlockData())

    /**
     * Revert [location] for [player] to the actual world block.
     */
    fun clear(player: Player, location: Location) {
        val key = locationKey(location)
        fakeBlocks[player.uniqueId]?.remove(key) ?: return
        player.sendBlockChange(location, location.block.blockData)
    }

    /**
     * Revert all fake blocks for [player].
     * Call this on [org.bukkit.event.player.PlayerQuitEvent] to avoid stale state.
     */
    fun clearAll(player: Player) {
        val entries = fakeBlocks.remove(player.uniqueId) ?: return
        entries.forEach { (key, _) ->
            val loc = keyToLocation(key, player.world)
            player.sendBlockChange(loc, loc.block.blockData)
        }
    }

    /**
     * Refresh all fake blocks for [player] — useful after a player respawns or
     * teleports (chunk load resets client-side fake blocks).
     */
    fun refresh(player: Player) {
        fakeBlocks[player.uniqueId]?.forEach { (key, data) ->
            player.sendBlockChange(keyToLocation(key, player.world), data)
        }
    }

    /** Whether [player] has a fake block at [location]. */
    fun has(player: Player, location: Location): Boolean =
        fakeBlocks[player.uniqueId]?.containsKey(locationKey(location)) == true

    /** The [BlockData] this player sees at [location], or the real block data if not overridden. */
    fun get(player: Player, location: Location): BlockData =
        fakeBlocks[player.uniqueId]?.get(locationKey(location)) ?: location.block.blockData

    private fun locationKey(loc: Location): Long {
        val x = loc.blockX.toLong() and 0x1FFFFFFL
        val y = (loc.blockY.toLong() + 2048) and 0xFFFL
        val z = loc.blockZ.toLong() and 0x1FFFFFFL
        return (x shl 38) or (y shl 26) or z
    }

    private fun keyToLocation(key: Long, world: org.bukkit.World): Location {
        val x = ((key shr 38) shl 39 shr 39).toInt()
        val y = (((key shr 26) and 0xFFF) - 2048).toInt()
        val z = ((key and 0x3FFFFFF).toLong() shl 38 shr 38).toInt()
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }
}
