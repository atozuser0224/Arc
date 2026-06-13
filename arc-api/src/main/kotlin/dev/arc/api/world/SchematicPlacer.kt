@file:JvmName("Schematics")

package dev.arc.api.world

import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.phys.Vec3
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.generator.CraftLimitedRegion
import org.bukkit.generator.BlockPopulator
import org.bukkit.generator.LimitedRegion
import org.bukkit.generator.WorldInfo
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.Random

/**
 * Registers placement rules for vanilla NBT structure files during world generation.
 * Structures are placed via [BlockPopulator] so they have full access to [LimitedRegion]
 * and can be used alongside other world-gen hooks.
 *
 * ```kotlin
 * val placer = plugin.schematicPlacer(world) {
 *     place(plugin.dataFolder.resolve("structures/tower.nbt")) {
 *         // called once per chunk — return the Y to place at, or null to skip
 *         origin { lrg, cx, cz ->
 *             if (random.nextInt(80) == 0)
 *                 BlockVector3(cx * 16 + 8, lrg.getHighestBlockYAt(cx * 16 + 8, cz * 16 + 8), cz * 16 + 8)
 *             else null
 *         }
 *     }
 * }
 * // On disable:
 * placer.close()
 * ```
 */
class SchematicPlacer internal constructor(val plugin: Plugin, val world: World) {

    private val populators = mutableListOf<StructurePopulator>()

    internal fun addRule(file: File, rule: PlacementRule) {
        populators += StructurePopulator(file, rule)
    }

    internal fun register() {
        populators.forEach { world.populators.add(it) }
    }

    /** Remove all registered populators. Call from plugin disable. */
    fun close() {
        world.populators.removeAll(populators.toSet())
        populators.clear()
    }
}

/** DSL builder for a single structure file's placement rules. */
class PlacementRule {
    internal var originFn: ((LimitedRegion, Int, Int, Random) -> BlockVector3?)? = null
    internal var mirror: net.minecraft.world.level.block.Mirror = net.minecraft.world.level.block.Mirror.NONE
    internal var rotation: net.minecraft.world.level.block.Rotation = net.minecraft.world.level.block.Rotation.NONE

    /**
     * Provide an origin block position for placement. Return `null` to skip this chunk.
     * [chunkX] and [chunkZ] are chunk coordinates (divide by 16 to get block origin).
     */
    fun origin(fn: (region: LimitedRegion, chunkX: Int, chunkZ: Int, random: Random) -> BlockVector3?) {
        originFn = fn
    }

    /** Random mirroring (NONE, LEFT_RIGHT, FRONT_BACK). Default: NONE. */
    fun mirror(m: net.minecraft.world.level.block.Mirror) { mirror = m }

    /** Random rotation (NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90). Default: NONE. */
    fun rotation(r: net.minecraft.world.level.block.Rotation) { rotation = r }
}

/** Integer block coordinate triple for schematic placement origins. */
data class BlockVector3(val x: Int, val y: Int, val z: Int)

private class StructurePopulator(
    private val file: File,
    private val rule: PlacementRule,
) : BlockPopulator() {

    private val template: StructureTemplate by lazy {
        val tag = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap())
        StructureTemplate().also { it.load(tag) }
    }

    override fun populate(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int, limitedRegion: LimitedRegion) {
        val origin = rule.originFn?.invoke(limitedRegion, chunkX, chunkZ, random) ?: return
        val nmsRegion = (limitedRegion as CraftLimitedRegion).handle
        val blockPos = net.minecraft.core.BlockPos(origin.x, origin.y, origin.z)
        val rng = net.minecraft.util.RandomSource.create(random.nextLong())
        val settings = StructurePlaceSettings()
            .setMirror(rule.mirror)
            .setRotation(rule.rotation)
            .setRandom(rng)
        template.placeInWorld(nmsRegion, blockPos, blockPos, settings, rng, 2)
    }
}

/** DSL builder for [SchematicPlacer]. */
class SchematicPlacerBuilder(val plugin: Plugin, val world: World) {
    private val placer = SchematicPlacer(plugin, world)

    /** Register a vanilla NBT structure file for placement. */
    fun place(file: File, block: PlacementRule.() -> Unit) {
        val rule = PlacementRule().apply(block)
        placer.addRule(file, rule)
    }

    internal fun build(): SchematicPlacer {
        placer.register()
        return placer
    }
}

/**
 * Register a [SchematicPlacer] that places vanilla `.nbt` structures during world generation.
 * Structures are placed by a [BlockPopulator] so they respect chunk boundaries via [LimitedRegion].
 */
fun Plugin.schematicPlacer(world: World, block: SchematicPlacerBuilder.() -> Unit): SchematicPlacer =
    SchematicPlacerBuilder(this, world).apply(block).build()
