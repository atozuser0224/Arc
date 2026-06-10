@file:JvmName("WorldChunk")

package dev.arc.api.world

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Biome

/**
 * World & chunk helpers over stable Bukkit API - heightmap, force-loading, biome access. (Direct
 * `LevelChunk`/`ChunkAccess`/light-engine pokes are reachable via `world.nms` when truly needed.)
 */

/** Highest non-air block Y at ([x], [z]). */
public fun World.highestBlockYAt(x: Int, z: Int): Int = getHighestBlockYAt(x, z)

/** Keep (or release) the chunk at ([chunkX], [chunkZ]) loaded regardless of players. */
public fun World.forceLoadChunk(chunkX: Int, chunkZ: Int, value: Boolean = true) {
    setChunkForceLoaded(chunkX, chunkZ, value)
}

/** Whether this chunk is kept loaded. */
public var Chunk.forceLoaded: Boolean
    get() = isForceLoaded
    set(value) {
        isForceLoaded = value
    }

/** The biome at this location. */
public val Location.biome: Biome
    get() = block.biome

/** Set the biome at this location. */
public fun Location.setBiome(biome: Biome) {
    world?.setBiome(blockX, blockY, blockZ, biome)
}
