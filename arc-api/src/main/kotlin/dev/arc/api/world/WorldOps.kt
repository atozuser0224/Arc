@file:JvmName("WorldOps")

package dev.arc.api.world

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.util.ArrayDeque

// ---------------------------------------------------------------------------
//  Cuboid region
// ---------------------------------------------------------------------------

data class Cuboid(val world: World, val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int) {
    val minX get() = minOf(x1, x2); val maxX get() = maxOf(x1, x2)
    val minY get() = minOf(y1, y2); val maxY get() = maxOf(y1, y2)
    val minZ get() = minOf(z1, z2); val maxZ get() = maxOf(z1, z2)
    val volume get() = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)

    companion object {
        fun of(a: Location, b: Location): Cuboid {
            require(a.world == b.world) { "Locations must be in same world" }
            return Cuboid(a.world, a.blockX, a.blockY, a.blockZ, b.blockX, b.blockY, b.blockZ)
        }
    }
}

// ---------------------------------------------------------------------------
//  Clipboard (copy / paste)
// ---------------------------------------------------------------------------

class Clipboard internal constructor(
    val width: Int,
    val height: Int,
    val depth: Int,
    internal val blocks: Array<BlockData>,
) {
    fun blockAt(x: Int, y: Int, z: Int): BlockData = blocks[x + width * (y + height * z)]

    suspend fun pasteAsync(
        origin: Location,
        ignoreAir: Boolean = false,
        progress: ((done: Int, total: Int) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        val world = origin.world
        var placed = 0
        val total = width * height * depth
        for (y in 0 until height) {
            for (z in 0 until depth) {
                for (x in 0 until width) {
                    val data = blockAt(x, y, z)
                    if (ignoreAir && data.material == Material.AIR) { placed++; continue }
                    world.setBlockData(
                        origin.blockX + x,
                        origin.blockY + y,
                        origin.blockZ + z,
                        data,
                    )
                    placed++
                    progress?.invoke(placed, total)
                }
            }
        }
        placed
    }
}

// ---------------------------------------------------------------------------
//  Undo stack entry
// ---------------------------------------------------------------------------

internal class BlockSnapshot(val x: Int, val y: Int, val z: Int, val data: BlockData)

class WorldUndoStack(private val world: World, private val maxSnapshots: Int = 16) {
    private val stack = ArrayDeque<List<BlockSnapshot>>()

    internal fun push(snapshots: List<BlockSnapshot>) {
        if (stack.size >= maxSnapshots) stack.removeLast()
        stack.push(snapshots)
    }

    suspend fun undo(): Boolean {
        val snapshots = stack.pollFirst() ?: return false
        withContext(Dispatchers.IO) {
            snapshots.forEach { world.setBlockData(it.x, it.y, it.z, it.data) }
        }
        return true
    }

    fun clear() { stack.clear() }
    val size get() = stack.size
}

// ---------------------------------------------------------------------------
//  World extensions
// ---------------------------------------------------------------------------

/**
 * Asynchronously fill a [Cuboid] with [blockData].
 *
 * ```kotlin
 * plugin.launch {
 *     world.fillAsync(Cuboid.of(loc1, loc2), Material.STONE.createBlockData()) { done, total ->
 *         player.sendMessage("${done * 100 / total}%")
 *     }
 * }
 * ```
 */
suspend fun World.fillAsync(
    region: Cuboid,
    blockData: BlockData,
    undoStack: WorldUndoStack? = null,
    progress: ((done: Int, total: Int) -> Unit)? = null,
): Int = withContext(Dispatchers.IO) {
    val snapshots = if (undoStack != null) mutableListOf<BlockSnapshot>() else null
    var placed = 0
    val total = region.volume
    for (y in region.minY..region.maxY) {
        for (z in region.minZ..region.maxZ) {
            for (x in region.minX..region.maxX) {
                snapshots?.add(BlockSnapshot(x, y, z, getBlockAt(x, y, z).blockData))
                setBlockData(x, y, z, blockData)
                placed++
                progress?.invoke(placed, total)
            }
        }
    }
    snapshots?.let { undoStack!!.push(it) }
    placed
}

/**
 * Asynchronously replace [from] blocks with [to] inside [region].
 */
suspend fun World.replaceAsync(
    region: Cuboid,
    from: Material,
    to: BlockData,
    undoStack: WorldUndoStack? = null,
    progress: ((done: Int, total: Int) -> Unit)? = null,
): Int = withContext(Dispatchers.IO) {
    val snapshots = if (undoStack != null) mutableListOf<BlockSnapshot>() else null
    var replaced = 0
    val total = region.volume
    var checked = 0
    for (y in region.minY..region.maxY) {
        for (z in region.minZ..region.maxZ) {
            for (x in region.minX..region.maxX) {
                val block = getBlockAt(x, y, z)
                checked++
                if (block.type == from) {
                    snapshots?.add(BlockSnapshot(x, y, z, block.blockData))
                    setBlockData(x, y, z, to)
                    replaced++
                }
                progress?.invoke(checked, total)
            }
        }
    }
    snapshots?.let { undoStack!!.push(it) }
    replaced
}

/**
 * Asynchronously copy a [Cuboid] into a [Clipboard].
 *
 * ```kotlin
 * val clipboard = world.copyAsync(Cuboid.of(loc1, loc2))
 * clipboard.pasteAsync(targetLocation, ignoreAir = true)
 * ```
 */
suspend fun World.copyAsync(region: Cuboid): Clipboard = withContext(Dispatchers.IO) {
    val w = region.maxX - region.minX + 1
    val h = region.maxY - region.minY + 1
    val d = region.maxZ - region.minZ + 1
    val blocks = Array(w * h * d) { i ->
        val x = region.minX + (i % w)
        val y = region.minY + ((i / w) % h)
        val z = region.minZ + (i / (w * h))
        getBlockAt(x, y, z).blockData
    }
    Clipboard(w, h, d, blocks)
}
