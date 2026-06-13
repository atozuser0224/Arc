@file:JvmName("GameRuleOverrides")

package dev.arc.api.player

import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.dreeam.leaf.entity.PlayerGameRuleOverrides

/**
 * Per-player GameRule overrides. Each method takes priority over the world's GameRule
 * value for the specific player only. Pass `null` to revert to world default.
 *
 * Requires the Leaf fork's `PlayerGameRuleOverrides` NMS class.
 *
 * ```kotlin
 * // VIP keeps inventory on death
 * player.keepInventory = true
 *
 * // Revert to world rule
 * player.keepInventory = null
 *
 * // Instant respawn for a specific player
 * player.immediateRespawn = true
 * ```
 */

/** Override whether this player keeps their inventory on death. `null` = world default. */
var Player.keepInventory: Boolean?
    get() {
        val id = (this as CraftPlayer).handle.uuid
        return PlayerGameRuleOverrides.getKeepInventory(id)?.let { it }
            .let { PlayerGameRuleOverrides.getKeepInventory((this as CraftPlayer).handle.uuid) }
    }
    set(value) {
        PlayerGameRuleOverrides.setKeepInventory(uniqueId, value)
    }

/** Override whether killing mobs drops loot for this player. `null` = world default. */
var Player.mobLootEnabled: Boolean?
    get() = PlayerGameRuleOverrides.getDoMobLoot(uniqueId)
    set(value) = PlayerGameRuleOverrides.setDoMobLoot(uniqueId, value)

/** Override whether this player sees an immediate-respawn screen (no death screen). `null` = world default. */
var Player.immediateRespawn: Boolean?
    get() = PlayerGameRuleOverrides.getImmediateRespawn(uniqueId)
    set(value) = PlayerGameRuleOverrides.setImmediateRespawn(uniqueId, value)

/** Clear all per-player GameRule overrides, reverting to world defaults. */
fun Player.clearGameRuleOverrides() = PlayerGameRuleOverrides.clearAll(uniqueId)
