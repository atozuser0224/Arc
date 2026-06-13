@file:JvmName("WorldSnapshots")

package dev.arc.api.world

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.plugin.Plugin
import org.bukkit.util.BoundingBox
import java.time.Instant

/**
 * Snapshot and roll-back arbitrary world regions.
 *
 * Captures a copy of every block in a bounding box and can restore it later —
 * on the main thread or asynchronously (capture only). Useful on raid/loot servers
 * to revert griefed bases, reset arenas between rounds, or undo botched admin edits.
 *
 * ```kotlin
 * val snapper = plugin.worldSnapshot(world)
 *
 * // Take a snapshot before a raid starts
 * val before = snapper.capture(base.boundingBox)
 *
 * // After raid ends, restore to pre-raid state
 * snapper.restore(before)
 *
 * // See what changed
 * snapper.diff(before, snapper.capture(base.boundingBox))
 *     .forEach { (loc, old, new) -> println("$loc: $old → $new") }
 * ```
 */
class WorldSnapshot(private val world: World, private val plugin: Plugin) {

    data class BlockState(val location: Location, val data: BlockData)

    data class BlockDiff(val location: Location, val before: BlockData, val after: BlockData)

    /** An immutable snapshot of a region at a point in time. */
    data class Snapshot(
        val world: World,
        val region: BoundingBox,
        val capturedAt: Instant,
        internal val blocks: Map<Long, BlockData>,
    ) {
        val blockCount: Int get() = blocks.size

        override fun toString() =
            "Snapshot(${world.name} ${region.widthX.toInt()}x${region.height.toInt()}x${region.widthZ.toInt()}" +
                " @$capturedAt, $blockCount blocks)"
    }

    // ── Capture ────────────────────────────────────────────────────────────────

    /**
     * Capture all blocks in [box] synchronously.
     * Must be called from the main thread (block reads require world access).
     */
    fun capture(box: BoundingBox): Snapshot {
        val blocks = mutableMapOf<Long, BlockData>()
        iterateBox(box) { x, y, z ->
            val block = world.getBlockAt(x, y, z)
            blocks[encodeXYZ(x, y, z)] = block.blockData
        }
        return Snapshot(world, box, Instant.now(), blocks)
    }

    /**
     * Capture asynchronously; calls [callback] with the result on the main thread.
     * Safe to call from any thread — block reads are scheduled back to the main thread.
     */
    fun captureAsync(box: BoundingBox, callback: (Snapshot) -> Unit) {
        plugin.server.scheduler.runTask(plugin) { ->
            val snap = capture(box)
            callback(snap)
        }
    }

    // ── Restore ────────────────────────────────────────────────────────────────

    /**
     * Restore all blocks in [snapshot] to their captured state.
     * Must be called from the main thread.
     */
    fun restore(snapshot: Snapshot) {
        require(snapshot.world == world) { "Snapshot is from a different world" }
        snapshot.blocks.forEach { (encoded, data) ->
            val (x, y, z) = decodeXYZ(encoded)
            world.getBlockAt(x, y, z).blockData = data
        }
    }

    /**
     * Restore in chunks over multiple ticks to avoid freezing the server.
     * [blocksPerTick] controls how many blocks are restored per tick (~50ms budget).
     */
    fun restoreGradually(snapshot: Snapshot, blocksPerTick: Int = 500, onComplete: (() -> Unit)? = null) {
        require(snapshot.world == world) { "Snapshot is from a different world" }
        val entries = snapshot.blocks.entries.toList()
        var index = 0
        plugin.server.scheduler.runTaskTimer(plugin, { task ->
            val end = minOf(index + blocksPerTick, entries.size)
            for (i in index until end) {
                val (encoded, data) = entries[i]
                val (x, y, z) = decodeXYZ(encoded)
                world.getBlockAt(x, y, z).blockData = data
            }
            index = end
            if (index >= entries.size) {
                task.cancel()
                onComplete?.invoke()
            }
        }, 0L, 1L)
    }

    // ── Diff ───────────────────────────────────────────────────────────────────

    /**
     * Compare two snapshots of the same region and return all blocks that changed.
     */
    fun diff(before: Snapshot, after: Snapshot): List<BlockDiff> {
        val result = mutableListOf<BlockDiff>()
        val allKeys = before.blocks.keys + after.blocks.keys
        for (key in allKeys.toSet()) {
            val b = before.blocks[key] ?: continue
            val a = after.blocks[key] ?: continue
            if (b.material != a.material || b.asString != a.asString) {
                val (x, y, z) = decodeXYZ(key)
                result += BlockDiff(Location(world, x.toDouble(), y.toDouble(), z.toDouble()), b, a)
            }
        }
        return result
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun iterateBox(box: BoundingBox, action: (Int, Int, Int) -> Unit) {
        val minX = box.minX.toInt(); val maxX = box.maxX.toInt()
        val minY = box.minY.toInt(); val maxY = box.maxY.toInt()
        val minZ = box.minZ.toInt(); val maxZ = box.maxZ.toInt()
        for (y in minY..maxY) for (x in minX..maxX) for (z in minZ..maxZ) action(x, y, z)
    }

    // Pack xyz into a long; y limited to [-512, 511], x/z to [-1M, 1M] (safe for Minecraft)
    private fun encodeXYZ(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFL) or ((y.toLong() and 0xFFFFFL) shl 22) or ((z.toLong() and 0x3FFFFFL) shl 42)

    private fun decodeXYZ(v: Long): Triple<Int, Int, Int> {
        val x = (v and 0x3FFFFFL).toInt().let { if (it and 0x200000 != 0) it or -0x400000 else it }
        val y = ((v shr 22) and 0xFFFFFL).toInt().let { if (it and 0x80000 != 0) it or -0x100000 else it }
        val z = ((v shr 42) and 0x3FFFFFL).toInt().let { if (it and 0x200000 != 0) it or -0x400000 else it }
        return Triple(x, y, z)
    }
}

/** Create a [WorldSnapshot] helper for this plugin and [world]. */
fun Plugin.worldSnapshot(world: World): WorldSnapshot = WorldSnapshot(world, this)
