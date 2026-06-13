@file:JvmName("NoiseAccessors")

package dev.arc.api.world

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld

/**
 * Low-level access to the noise-based terrain generator for a world.
 * Queries the same data that drives raw terrain shape — before decoration or surface rules.
 *
 * Only works for worlds backed by [NoiseBasedChunkGenerator] (overworld, nether, end).
 * Flat/custom generators will fall back to the Bukkit `getHighestBlockYAt` API.
 *
 * ```kotlin
 * val accessor = world.noiseAccessor
 * val height = accessor.getTerrainHeight(512, -128)    // raw surface Y
 * val column = accessor.sampleColumn(512, -128)        // block-by-block from minY to surface
 * ```
 */
class NoiseAccessor(val world: World) {

    private val nmsLevel: ServerLevel = (world as CraftWorld).handle
    private val gen: NoiseBasedChunkGenerator? =
        nmsLevel.chunkSource.generator as? NoiseBasedChunkGenerator

    private val randomState get() = nmsLevel.chunkSource.randomState

    /**
     * Raw terrain surface height at [blockX], [blockZ] — the Y of the highest solid noise block
     * before surface rules or decoration run. Returns [World.getHighestBlockYAt] fallback if
     * the world uses a non-noise generator.
     */
    fun getTerrainHeight(blockX: Int, blockZ: Int): Int =
        gen?.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE, nmsLevel, randomState)
            ?: world.getHighestBlockYAt(blockX, blockZ)

    /**
     * Raw ocean floor height — highest solid block ignoring water/air. Useful for finding
     * seabed Y in ocean biomes during generation.
     */
    fun getOceanFloorHeight(blockX: Int, blockZ: Int): Int =
        gen?.getBaseHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG, nmsLevel, randomState)
            ?: world.getHighestBlockYAt(blockX, blockZ)

    /**
     * Sample the full noise column at [blockX], [blockZ].
     * Returns a map of Y → [Material] from [World.getMinHeight] to [getTerrainHeight].
     * Air entries are omitted. Expensive — avoid calling per-block in hot loops.
     */
    fun sampleColumn(blockX: Int, blockZ: Int): Map<Int, Material> {
        val result = LinkedHashMap<Int, Material>()
        if (gen == null) return result
        val column = gen.getBaseColumn(blockX, blockZ, nmsLevel, randomState)
        for (y in nmsLevel.minY until getTerrainHeight(blockX, blockZ)) {
            val state = column.getBlock(y)
            if (!state.isAir) {
                result[y] = state.getBukkitMaterial()
            }
        }
        return result
    }

    /** Returns true if this world uses a noise-based generator. */
    val isNoiseBased: Boolean get() = gen != null
}

private fun net.minecraft.world.level.block.state.BlockState.getBukkitMaterial(): Material =
    org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(this).material

/** Noise terrain accessor for this world. */
val World.noiseAccessor: NoiseAccessor get() = NoiseAccessor(this)
