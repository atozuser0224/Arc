@file:JvmName("ClientView")

package dev.arc.api.client

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * Per-player **client-side illusions** through Paper's public "send change" API - the safe, version-stable way
 * to do what people reach for raw packets for: show one player something the server world doesn't actually
 * contain. No NMS, no desync risk from hand-rolled packets.
 *
 * What the client sees here is purely visual and reverts on the next real block/chunk update (or via the
 * matching reset call).
 *
 * ```kotlin
 * player.showFakeBlock(loc, Material.DIAMOND_BLOCK.createBlockData())   // only this player sees diamond
 * player.showFakeEquipment(otherPlayer, EquipmentSlot.HEAD, pumpkin)    // fake hat, just for them
 * ```
 */

/** Show [data] at [location] to this player only (does not change the real world). */
public fun Player.showFakeBlock(location: Location, data: BlockData) {
    sendBlockChange(location, data)
}

/** Revert a [showFakeBlock] by re-sending the block's real state to this player. */
public fun Player.resetFakeBlock(location: Location) {
    sendBlockChange(location, location.block.blockData)
}

/** Show a block-breaking crack overlay (0.0–1.0 progress; outside that range clears it) to this player. */
public fun Player.showBlockCrack(location: Location, progress: Float) {
    sendBlockDamage(location, progress)
}

/** Show fake [item] in [slot] on [entity], visible only to this player. */
public fun Player.showFakeEquipment(entity: LivingEntity, slot: EquipmentSlot, item: ItemStack) {
    sendEquipmentChange(entity, slot, item)
}

/** Show fake sign text at [location] (must be a sign block) to this player only. */
public fun Player.showFakeSign(location: Location, lines: List<Component>) {
    sendSignChange(location, lines)
}
