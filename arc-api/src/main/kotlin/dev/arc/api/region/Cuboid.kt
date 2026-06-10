@file:JvmName("Regions")

package dev.arc.api.region

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * An axis-aligned, inclusive box of block coordinates in a single [world].
 *
 * Plugins constantly need "the blocks/area between these two corners" - for protection regions, fills,
 * particle boxes, spawn areas. [Cuboid] normalises the corners, answers membership tests, and exposes the
 * blocks as a **lazy [Sequence]** so iterating a huge region never materialises a giant list.
 *
 * ```kotlin
 * val region = cuboidOf(pos1, pos2)
 * if (player.location in region) deny()
 * region.blocks().filter { it.type.isAir }.forEach { it.type = Material.STONE }
 * ```
 */
public class Cuboid(
    public val world: World,
    x1: Int, y1: Int, z1: Int,
    x2: Int, y2: Int, z2: Int,
) {
    public val minX: Int = min(x1, x2)
    public val minY: Int = min(y1, y2)
    public val minZ: Int = min(z1, z2)
    public val maxX: Int = max(x1, x2)
    public val maxY: Int = max(y1, y2)
    public val maxZ: Int = max(z1, z2)

    public val sizeX: Int get() = maxX - minX + 1
    public val sizeY: Int get() = maxY - minY + 1
    public val sizeZ: Int get() = maxZ - minZ + 1

    /** Total block count. */
    public val volume: Int get() = sizeX * sizeY * sizeZ

    /** Center of the region as a block-centered [Location]. */
    public val center: Location
        get() = Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5)

    /** `true` if [location] is in the same world and within the inclusive bounds. */
    public operator fun contains(location: Location): Boolean =
        location.world == world &&
            location.blockX in minX..maxX &&
            location.blockY in minY..maxY &&
            location.blockZ in minZ..maxZ

    /** Lazily yield every [Block] in the region (x, then z, then y). */
    public fun blocks(): Sequence<Block> = sequence {
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    yield(world.getBlockAt(x, y, z))
                }
            }
        }
    }

    /** A uniformly random block-centered location inside the region. */
    public fun randomLocation(): Location = Location(
        world,
        Random.nextInt(minX, maxX + 1) + 0.5,
        Random.nextInt(minY, maxY + 1) + 0.5,
        Random.nextInt(minZ, maxZ + 1) + 0.5,
    )

    /** A new cuboid grown by [amount] blocks on every face. */
    public fun expand(amount: Int): Cuboid =
        Cuboid(world, minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount)

    override fun toString(): String =
        "Cuboid(${world.name}, [$minX,$minY,$minZ]..[$maxX,$maxY,$maxZ])"
}

/** Build a [Cuboid] from two corner locations. Both must share a (non-null) world. */
public fun cuboidOf(a: Location, b: Location): Cuboid {
    val world = requireNotNull(a.world) { "corner 'a' has no world" }
    require(a.world == b.world) { "corners must be in the same world" }
    return Cuboid(world, a.blockX, a.blockY, a.blockZ, b.blockX, b.blockY, b.blockZ)
}
