@file:JvmName("ChunkGeneration")

package dev.arc.api.world

import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.dreeam.leaf.event.ChunkCarveEvent
import org.dreeam.leaf.event.ChunkNoiseGenerateEvent
import org.dreeam.leaf.event.ChunkSurfaceBuildEvent

/**
 * Hooks into chunk generation phases for a specific world or all worlds.
 *
 * Each phase can be filtered by returning `false` to cancel that step:
 *
 * ```kotlin
 * plugin.chunkGenerationModifier {
 *     // Disable caves in the "flatworld" world
 *     filterCarvers { world, _, _ -> world.name != "flatworld" }
 *
 *     // Skip surface rules for every 4th chunk column (checkerboard stone)
 *     filterSurface { _, cx, cz -> (cx + cz) % 4 != 0 }
 * }
 * ```
 *
 * Call [close] on plugin disable to unregister all listeners.
 */
class ChunkGenerationModifier internal constructor(val plugin: Plugin) : Listener {

    private val noiseFilters = mutableListOf<(World, Int, Int) -> Boolean>()
    private val surfaceFilters = mutableListOf<(World, Int, Int) -> Boolean>()
    private val carveFilters = mutableListOf<(World, Int, Int) -> Boolean>()

    @EventHandler
    fun onNoise(event: ChunkNoiseGenerateEvent) {
        if (noiseFilters.any { !it(event.world, event.chunkX, event.chunkZ) })
            event.isCancelled = true
    }

    @EventHandler
    fun onSurface(event: ChunkSurfaceBuildEvent) {
        if (surfaceFilters.any { !it(event.world, event.chunkX, event.chunkZ) })
            event.isCancelled = true
    }

    @EventHandler
    fun onCarve(event: ChunkCarveEvent) {
        if (carveFilters.any { !it(event.world, event.chunkX, event.chunkZ) })
            event.isCancelled = true
    }

    /**
     * Cancel noise fill (base terrain) when [predicate] returns `false`.
     * A cancelled noise phase produces a chunk with no blocks at all.
     */
    fun filterNoise(predicate: (world: World, chunkX: Int, chunkZ: Int) -> Boolean): ChunkGenerationModifier {
        noiseFilters += predicate
        return this
    }

    /**
     * Cancel surface building when [predicate] returns `false`.
     * Chunks will keep raw stone/deepslate instead of biome-appropriate surfaces.
     */
    fun filterSurface(predicate: (world: World, chunkX: Int, chunkZ: Int) -> Boolean): ChunkGenerationModifier {
        surfaceFilters += predicate
        return this
    }

    /**
     * Cancel cave carving when [predicate] returns `false`.
     * Affected chunks will be solid with no caves, ravines, or chasms.
     */
    fun filterCarvers(predicate: (world: World, chunkX: Int, chunkZ: Int) -> Boolean): ChunkGenerationModifier {
        carveFilters += predicate
        return this
    }

    /** Unregister all listeners. Call from your plugin's [org.bukkit.plugin.java.JavaPlugin.onDisable]. */
    fun close() {
        HandlerList.unregisterAll(this)
    }
}

/**
 * Create and register a [ChunkGenerationModifier] for this plugin.
 * The modifier is active immediately and fires on all worlds unless filtered inside [block].
 */
fun Plugin.chunkGenerationModifier(block: ChunkGenerationModifier.() -> Unit): ChunkGenerationModifier {
    val modifier = ChunkGenerationModifier(this).apply(block)
    server.pluginManager.registerEvents(modifier, this)
    return modifier
}
