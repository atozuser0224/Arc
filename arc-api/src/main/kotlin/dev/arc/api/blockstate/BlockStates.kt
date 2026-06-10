@file:JvmName("BlockStates")

package dev.arc.api.blockstate

import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.Container
import org.bukkit.block.CreatureSpawner
import org.bukkit.block.Sign
import org.bukkit.block.sign.Side
import org.bukkit.entity.EntityType
import org.bukkit.inventory.Inventory

/**
 * BlockEntity (tile-entity) data access via the Bukkit [BlockState] snapshot API - signs, containers,
 * spawners. (Raw `BlockEntity`/NBT is reachable through `block.nms`, but the snapshot API is the safe path.)
 */

/** This block's state cast to [T] (e.g. `block.stateAs<Sign>()`), or `null`. */
public inline fun <reified T : BlockState> Block.stateAs(): T? = state as? T

/** Set the front-side text of a sign block (up to 4 lines) and apply it. */
public fun Block.editSign(lines: List<Component>) {
    val sign = state as? Sign ?: return
    val front = sign.getSide(Side.FRONT)
    lines.forEachIndexed { index, line -> if (index < 4) front.line(index, line) }
    sign.update()
}

/** The inventory of a container block (chest, barrel, hopper, ...), or `null` if it is not a container. */
public fun Block.containerInventory(): Inventory? = (state as? Container)?.inventory

/** Set the mob a spawner produces and apply it. */
public fun Block.setSpawnerType(type: EntityType) {
    val spawner = state as? CreatureSpawner ?: return
    spawner.spawnedType = type
    spawner.update()
}
