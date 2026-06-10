@file:JvmName("Blocks")

package dev.arc.api.block

import org.bukkit.block.Block
import org.bukkit.block.BlockFace

/**
 * Adjacency and relative-block helpers - the small graph operations world-editing and redstone-ish plugins
 * lean on.
 */

/** The block [distance] steps in [face] direction. */
public fun Block.relativeTo(face: BlockFace, distance: Int = 1): Block = getRelative(face, distance)

/** The six face-adjacent blocks (N/S/E/W/up/down). */
public val Block.faceNeighbors: List<Block>
    get() = FACE_NEIGHBORS.map { getRelative(it) }

/** All 26 surrounding blocks (faces, edges and corners). */
public val Block.allNeighbors: List<Block>
    get() = buildList {
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            if (dx != 0 || dy != 0 || dz != 0) add(getRelative(dx, dy, dz))
        }
    }

/** Straight-line distance between two blocks' positions. */
public fun Block.distanceTo(other: Block): Double = location.distance(other.location)

private val FACE_NEIGHBORS = listOf(
    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN,
)
