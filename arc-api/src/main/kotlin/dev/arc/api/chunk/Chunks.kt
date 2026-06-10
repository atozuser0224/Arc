@file:JvmName("Chunks")

package dev.arc.api.chunk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity

/** Async chunk load — suspends until the chunk is ready without blocking the main thread. */
public suspend fun World.loadChunkAsync(chunkX: Int, chunkZ: Int): Chunk =
    withContext(Dispatchers.IO) {
        var result: Chunk? = null
        val future = getChunkAtAsync(chunkX, chunkZ)
        result = future.get()
        result
    }

/** Pre-generate all chunks in a radius around a center chunk (async, returns count). */
public suspend fun World.preloadChunks(centerX: Int, centerZ: Int, radius: Int): Int {
    var count = 0
    for (dx in -radius..radius) {
        for (dz in -radius..radius) {
            loadChunkAsync(centerX + dx, centerZ + dz)
            count++
        }
    }
    return count
}

/** All loaded chunks within [radiusBlocks] of [location]. */
public fun World.nearbyLoadedChunks(location: Location, radiusBlocks: Double): List<Chunk> {
    val chunkRadius = (radiusBlocks / 16).toInt() + 1
    val cx = location.blockX shr 4
    val cz = location.blockZ shr 4
    return loadedChunks.filter {
        val dx = it.x - cx
        val dz = it.z - cz
        dx * dx + dz * dz <= chunkRadius * chunkRadius
    }
}

/** Get all entities in chunks within [radiusChunks] of [chunk]. */
public fun Chunk.nearbyEntities(radiusChunks: Int = 1): List<Entity> {
    val result = mutableListOf<Entity>()
    for (dx in -radiusChunks..radiusChunks) {
        for (dz in -radiusChunks..radiusChunks) {
            if (world.isChunkLoaded(x + dx, z + dz)) {
                result += world.getChunkAt(x + dx, z + dz).entities.toList()
            }
        }
    }
    return result
}

/** Take an async [ChunkSnapshot] without blocking the server thread. */
public suspend fun Chunk.snapshotAsync(): ChunkSnapshot = withContext(Dispatchers.IO) {
    getChunkSnapshot(true, true, false)
}
