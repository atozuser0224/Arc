package dev.arc.api.math

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

/** Vector / location operator sugar (all non-mutating — they clone first). */
operator fun Location.plus(v: Vector): Location = clone().add(v)
operator fun Location.minus(v: Vector): Location = clone().subtract(v)
operator fun Vector.plus(o: Vector): Vector = clone().add(o)
operator fun Vector.minus(o: Vector): Vector = clone().subtract(o)
operator fun Vector.times(scalar: Double): Vector = clone().multiply(scalar)

/**
 * Lazily walk every [Block] in the cuboid spanned by `this` and [other] without
 * materialising the whole region — blocks are produced one at a time, so a
 * 100×100×100 selection costs O(1) memory instead of allocating a million refs.
 *
 * ```
 * for (block in a.blocksTo(b)) if (block.type == Material.CHEST) ...
 * ```
 */
fun Location.blocksTo(other: Location): Sequence<Block> {
    val w: World = world ?: error("Location has no world")
    val minX = min(blockX, other.blockX); val maxX = max(blockX, other.blockX)
    val minY = min(blockY, other.blockY); val maxY = max(blockY, other.blockY)
    val minZ = min(blockZ, other.blockZ); val maxZ = max(blockZ, other.blockZ)
    return sequence {
        for (x in minX..maxX)
            for (y in minY..maxY)
                for (z in minZ..maxZ)
                    yield(w.getBlockAt(x, y, z))
    }
}
