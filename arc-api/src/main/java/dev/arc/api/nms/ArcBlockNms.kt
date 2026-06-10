package dev.arc.api.nms

import dev.arc.api.scheduling.callRegion
import dev.arc.api.scheduling.BatchResult
import dev.arc.api.scheduling.dispatchRegionBatch
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

data class BlockMutation(val block: Block, val data: BlockData)

/** Block/world capabilities that require server internals (NMS). */
interface ArcBlockNms {

    /**
     * Set a block **without** triggering physics / neighbour updates — useful
     * for bulk edits where you don't want cascading block updates. Clients are
     * still notified. Returns whether the change was applied.
     */
    fun setNoPhysics(block: Block, data: BlockData): Boolean

    /** Emitted (block-source) light level at [block] (0-15). */
    fun blockLight(block: Block): Int

    /** Sky light level at [block] (0-15). */
    fun skyLight(block: Block): Int

    /** The raw `net.minecraft.world.level.ServerLevel` for the block's world. */
    fun levelHandle(block: Block): Any?

    /** Set a block on its owning region thread without blocking the caller. */
    fun setNoPhysicsAsync(
        plugin: Plugin,
        block: Block,
        data: BlockData,
    ): CompletableFuture<Boolean> = plugin.callRegion(block.location) { setNoPhysics(block, data) }

    fun blockLightAsync(plugin: Plugin, block: Block): CompletableFuture<Int> =
        plugin.callRegion(block.location) { blockLight(block) }

    fun skyLightAsync(plugin: Plugin, block: Block): CompletableFuture<Int> =
        plugin.callRegion(block.location) { skyLight(block) }

    /**
     * Apply a large block mutation set over multiple region ticks. NMS writes
     * stay on owning threads while time and concurrency limits provide backpressure.
     */
    fun setNoPhysicsBatchAsync(
        plugin: Plugin,
        mutations: Iterable<BlockMutation>,
        maxMillisPerTick: Long = dev.arc.api.Arc.settings.tickBudgetMillis,
        maxConcurrentRegions: Int = dev.arc.api.Arc.settings.regionBatchConcurrency,
        failFast: Boolean = false,
    ): CompletableFuture<BatchResult> = plugin.dispatchRegionBatch(
        items = mutations,
        locationOf = { it.block.location },
        maxMillisPerTick = maxMillisPerTick,
        maxConcurrentRegions = maxConcurrentRegions,
        failFast = failFast,
    ) { mutation ->
        check(setNoPhysics(mutation.block, mutation.data)) {
            "NMS block mutation was rejected at ${mutation.block.location}"
        }
    }
}
