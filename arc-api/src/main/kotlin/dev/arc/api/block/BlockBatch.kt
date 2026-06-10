@file:JvmName("BlockBatch")

package dev.arc.api.block

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData

/**
 * Fill a rectangular region with [material] using Paper's block-state batch API
 * (skips lighting/physics/events per individual block — much faster than setBlock in a loop).
 */
public fun World.fillRegion(
    from: Location,
    to: Location,
    material: Material,
    applyPhysics: Boolean = false,
) {
    val x1 = minOf(from.blockX, to.blockX)
    val y1 = minOf(from.blockY, to.blockY)
    val z1 = minOf(from.blockZ, to.blockZ)
    val x2 = maxOf(from.blockX, to.blockX)
    val y2 = maxOf(from.blockY, to.blockY)
    val z2 = maxOf(from.blockZ, to.blockZ)
    val data = material.createBlockData()
    for (x in x1..x2) for (y in y1..y2) for (z in z1..z2) {
        getBlockAt(x, y, z).setBlockData(data, applyPhysics)
    }
}

/** Apply a [block] transform to every block in the region. */
public fun World.editRegion(
    from: Location,
    to: Location,
    block: (x: Int, y: Int, z: Int) -> BlockData?,
) {
    val x1 = minOf(from.blockX, to.blockX)
    val y1 = minOf(from.blockY, to.blockY)
    val z1 = minOf(from.blockZ, to.blockZ)
    val x2 = maxOf(from.blockX, to.blockX)
    val y2 = maxOf(from.blockY, to.blockY)
    val z2 = maxOf(from.blockZ, to.blockZ)
    for (x in x1..x2) for (y in y1..y2) for (z in z1..z2) {
        block(x, y, z)?.let { getBlockAt(x, y, z).setBlockData(it, false) }
    }
}

/** Replace all occurrences of [from] material with [to] in the region. */
public fun World.replaceInRegion(
    corner1: Location,
    corner2: Location,
    from: Material,
    to: Material,
) {
    val toData = to.createBlockData()
    editRegion(corner1, corner2) { x, y, z ->
        if (getBlockAt(x, y, z).type == from) toData else null
    }
}
